package br.com.samuelfrutas.entregas.data

import android.os.Handler
import android.os.Looper
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class FirebaseSync(
    private val dbLocal: EntregaDbHelper
) {
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    @Volatile var status: String = "AGUARDANDO INTERNET"
        private set

    fun sync(e: EntregaLocal, onDone: ((Boolean) -> Unit)? = null) {
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
