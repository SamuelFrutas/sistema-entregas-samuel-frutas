package br.com.samuelfrutas.entregas.data

import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class FirebaseSync(
    private val dbLocal: EntregaDbHelper
) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private var realtimeListener: ListenerRegistration? = null

    @Volatile var status: String = "AGUARDANDO INTERNET"
        private set

    fun pull(e: EntregaLocal, onDone: ((Boolean) -> Unit)? = null) {
        if (e.id == 0L) { status = "ERRO: ID LOCAL INVÁLIDO"; onDone?.invoke(false); return }
        ensureAuth { ok, authError ->
            if (!ok) {
                status = "ERRO FIREBASE: " + (authError ?: "autenticação")
                onDone?.invoke(false)
                return@ensureAuth
            }
            status = "RECEBENDO ALTERAÇÕES..."
            firestore.collection("entregas").document(e.id.toString()).get()
                .addOnSuccessListener { snap ->
                    if (!snap.exists()) {
                        status = "NÃO ENCONTRADO NO FIREBASE"
                        onDone?.invoke(false)
                        return@addOnSuccessListener
                    }
                    val atualizado = e.copy(
                        dia = snap.getString("dia") ?: e.dia,
                        predio = snap.getString("predio") ?: e.predio,
                        bloco = snap.getString("bloco") ?: e.bloco,
                        apartamento = snap.getString("apartamento") ?: e.apartamento,
                        semEndereco = snap.getBoolean("semEndereco") ?: e.semEndereco,
                        enderecoReferencia = snap.getString("enderecoReferencia") ?: e.enderecoReferencia,
                        valorCompraCentavos = snap.getLong("valorCompraCentavos") ?: e.valorCompraCentavos,
                        pagamentoInicial = snap.getString("pagamentoInicial") ?: e.pagamentoInicial,
                        resultadoPagamento = snap.getString("resultadoPagamento") ?: e.resultadoPagamento,
                        formaPagamento = snap.getString("formaPagamento") ?: e.formaPagamento,
                        caixinhaCentavos = snap.getLong("caixinhaCentavos") ?: e.caixinhaCentavos,
                        observacao = snap.getString("observacao") ?: e.observacao,
                        realizada = snap.getBoolean("realizada") ?: e.realizada,
                        sincronizacao = "SINCRONIZADA"
                    )
                    dbLocal.substituirDoServidor(atualizado)
                    status = "RECEBIDO DO FIREBASE"
                    onDone?.invoke(true)
                }
                .addOnFailureListener { ex ->
                    status = "ERRO AO RECEBER: " + (ex.message?.replace("\n", " ")?.take(140) ?: ex.javaClass.simpleName)
                    onDone?.invoke(false)
                }
        }
    }

    fun startRealtimeSync() {
        if (realtimeListener != null) return
        ensureAuth { ok, authError ->
            if (!ok) {
                status = "ERRO FIREBASE: ${authError ?: "autenticação"}"
                return@ensureAuth
            }
            status = "CONECTADO AO FIREBASE"
            realtimeListener = firestore.collection("entregas")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        status = "ERRO RECEBIMENTO: " + (error.message?.replace("\n", " ")?.take(140) ?: error.javaClass.simpleName)
                        return@addSnapshotListener
                    }
                    if (snapshot == null) return@addSnapshotListener

                    for (change in snapshot.documentChanges) {
                        val doc = change.document
                        val id = doc.id.toLongOrNull() ?: continue
                        if (change.type.name == "REMOVED") {
                            dbLocal.excluir(id)
                            continue
                        }
                        val e = EntregaLocal(
                            id = id,
                            dia = doc.getString("dia") ?: "",
                            predio = doc.getString("predio") ?: "",
                            bloco = doc.getString("bloco") ?: "",
                            apartamento = doc.getString("apartamento") ?: "",
                            semEndereco = doc.getBoolean("semEndereco") ?: false,
                            enderecoReferencia = doc.getString("enderecoReferencia") ?: "",
                            valorCompraCentavos = doc.getLong("valorCompraCentavos"),
                            pagamentoInicial = doc.getString("pagamentoInicial") ?: "NAO_INFORMADO",
                            resultadoPagamento = doc.getString("resultadoPagamento") ?: "",
                            formaPagamento = doc.getString("formaPagamento") ?: "",
                            caixinhaCentavos = doc.getLong("caixinhaCentavos") ?: 0L,
                            observacao = doc.getString("observacao") ?: "",
                            realizada = doc.getBoolean("realizada") ?: false,
                            sincronizacao = "SINCRONIZADA"
                        )
                        dbLocal.sincronizarDoFirestore(e)
                    }
                    status = "SINCRONIZADO"
                }
        }
    }

    fun stopRealtimeSync() {
        realtimeListener?.remove()
        realtimeListener = null
    }

    fun getValorPorEntrega(onDone: (Long?) -> Unit) {
        ensureAuth { ok, _ ->
            if (!ok) {
                onDone(null)
                return@ensureAuth
            }
            firestore.collection("configuracoes").document("entregador").get()
                .addOnSuccessListener { snap ->
                    onDone(snap.getLong("valorEntregaCentavos"))
                }
                .addOnFailureListener {
                    onDone(null)
                }
        }
    }

    fun sync(e: EntregaLocal, onDone: ((Boolean) -> Unit)? = null) {
        status = "VERIFICANDO CONEXÃO..."
        if (e.id == 0L) { status = "ERRO: ID LOCAL INVÁLIDO"; onDone?.invoke(false); return }
        syncAttempt(e, onDone, 0)
    }

    private fun syncAttempt(e: EntregaLocal, onDone: ((Boolean) -> Unit)?, attempt: Int) {
        ensureAuth { ok, authError ->
            if (!ok) {
                status = "ERRO FIREBASE: ${authError ?: "autenticação"}"
                if (attempt < 2) Handler(Looper.getMainLooper()).postDelayed({ syncAttempt(e, onDone, attempt + 1) }, 2000L)
                else onDone?.invoke(false)
                return@ensureAuth
            }
            val data = hashMapOf<String, Any?>(
                "dia" to e.dia, "predio" to e.predio, "bloco" to e.bloco,
                "apartamento" to e.apartamento, "semEndereco" to e.semEndereco,
                "enderecoReferencia" to e.enderecoReferencia, "valorCompraCentavos" to e.valorCompraCentavos,
                "pagamentoInicial" to e.pagamentoInicial, "resultadoPagamento" to e.resultadoPagamento,
                "formaPagamento" to e.formaPagamento, "caixinhaCentavos" to e.caixinhaCentavos,
                "observacao" to e.observacao, "realizada" to e.realizada,
                "sincronizacao" to "SINCRONIZADA", "atualizadoEm" to FieldValue.serverTimestamp()
            )
            status = "SINCRONIZANDO..." + if (attempt > 0) " TENTATIVA ${attempt + 1}/3" else ""
            firestore.collection("entregas").document(e.id.toString()).set(data)
                .addOnSuccessListener {
                    dbLocal.marcarSincronizada(e.id); status = "SINCRONIZADO"; onDone?.invoke(true)
                }
                .addOnFailureListener { ex ->
                    dbLocal.marcarSincronizacaoPendente(e.id)
                    status = "ERRO FIREBASE: " + (ex.message?.replace("\n", " ")?.take(140) ?: ex.javaClass.simpleName)
                    if (attempt < 2) Handler(Looper.getMainLooper()).postDelayed({ syncAttempt(e, onDone, attempt + 1) }, 2500L)
                    else onDone?.invoke(false)
                }
        }
    }

    fun delete(eId: Long, onDone: ((Boolean) -> Unit)? = null) {
        ensureAuth { ok, _ ->
            if (!ok) {
                onDone?.invoke(false)
                return@ensureAuth
            }
            firestore.collection("entregas").document(eId.toString()).delete()
                .addOnSuccessListener {
                    status = "SINCRONIZADO"
                    onDone?.invoke(true)
                }
                .addOnFailureListener {
                    status = "PENDENTE DE EXCLUSÃO"
                    onDone?.invoke(false)
                }
        }
    }

    private fun ensureAuth(done: (Boolean, String?) -> Unit) {
        if (auth.currentUser != null) { done(true, null); return }
        auth.signInAnonymously()
            .addOnSuccessListener { done(true, null) }
            .addOnFailureListener { ex -> done(false, ex.message ?: ex.javaClass.simpleName) }
    }
}
