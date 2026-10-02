package br.com.samuelfrutas.entregas.data

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
        ensureAuth { ok ->
            if (!ok) {
                status = "AGUARDANDO AUTENTICAÇÃO"
                onDone?.invoke(false)
                return@ensureAuth
            }

            val data = hashMapOf<String, Any?>(
                "dia" to e.dia,
                "predio" to e.predio,
                "bloco" to e.bloco,
                "apartamento" to e.apartamento,
                "semEndereco" to e.semEndereco,
                "enderecoReferencia" to e.enderecoReferencia,
                "valorCompraCentavos" to e.valorCompraCentavos,
                "pagamentoInicial" to e.pagamentoInicial,
                "resultadoPagamento" to e.resultadoPagamento,
                "formaPagamento" to e.formaPagamento,
                "caixinhaCentavos" to e.caixinhaCentavos,
                "observacao" to e.observacao,
                "realizada" to e.realizada,
                "sincronizacao" to "SINCRONIZADA",
                "atualizadoEm" to FieldValue.serverTimestamp()
            )
            if (e.id == 0L) {
                status = "ERRO: ID LOCAL INVÁLIDO"
                onDone?.invoke(false)
                return@ensureAuth
            }

            firestore.collection("entregas").document(e.id.toString()).set(data)
                .addOnSuccessListener {
                    dbLocal.marcarSincronizada(e.id)
                    status = "SINCRONIZADO"
                    onDone?.invoke(true)
                }
                .addOnFailureListener {
                    dbLocal.marcarSincronizacaoPendente(e.id)
                    status = "PENDENTE DE SINCRONIZAÇÃO"
                    onDone?.invoke(false)
                }
        }
    }

    fun delete(eId: Long, onDone: ((Boolean) -> Unit)? = null) {
        ensureAuth { ok ->
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

    private fun ensureAuth(done: (Boolean) -> Unit) {
        if (auth.currentUser != null) {
            done(true)
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { done(true) }
            .addOnFailureListener { done(false) }
    }
}
