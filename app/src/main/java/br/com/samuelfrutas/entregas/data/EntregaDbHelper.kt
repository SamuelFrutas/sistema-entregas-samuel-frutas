package br.com.samuelfrutas.entregas.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import br.com.samuelfrutas.entregas.MainActivity

class EntregaDbHelper(context: Context) : SQLiteOpenHelper(
    context, DATABASE_NAME, null, DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE entregas (
                id TEXT PRIMARY KEY NOT NULL,
                dia_entrega TEXT NOT NULL,
                predio TEXT,
                bloco TEXT,
                apartamento TEXT,
                sem_endereco INTEGER NOT NULL DEFAULT 0,
                endereco_referencia TEXT,
                valor_compra_centavos INTEGER,
                pagamento_inicial TEXT NOT NULL,
                resultado_pagamento TEXT,
                forma_pagamento TEXT,
                caixinha_centavos INTEGER NOT NULL DEFAULT 0,
                observacao TEXT,
                realizada INTEGER NOT NULL DEFAULT 0,
                sincronizacao_status TEXT NOT NULL DEFAULT 'PENDENTE',
                criado_em TEXT NOT NULL,
                atualizado_em TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("CREATE INDEX idx_entregas_dia ON entregas(dia_entrega)")
        db.execSQL("CREATE INDEX idx_entregas_sync ON entregas(sincronizacao_status)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // A versão 2 não altera o esquema; mantém os dados existentes.
    }

    fun saveDelivery(d: MainActivity.Delivery) {
        val values = ContentValues().apply {
            put("id", d.id)
            put("dia_entrega", d.day)
            put("endereco_referencia", d.location)
            put("valor_compra_centavos", parseMoneyToCents(d.purchaseValue))
            put("pagamento_inicial", d.initialPayment)
            put("resultado_pagamento", d.finalPayment)
            put("forma_pagamento", d.paymentMethod)
            put("caixinha_centavos", parseMoneyToCents(d.tip))
            put("observacao", d.observation)
            put("realizada", if (d.completed) 1 else 0)
            put("sincronizacao_status", "PENDENTE")
            put("criado_em", now())
            put("atualizado_em", now())
        }
        writableDatabase.insertWithOnConflict("entregas", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun loadDeliveries(day: String): List<MainActivity.Delivery> {
        val result = mutableListOf<MainActivity.Delivery>()
        readableDatabase.query(
            "entregas", null, "dia_entrega=?", arrayOf(day), null, null, "criado_em ASC"
        ).use { c ->
            val id=c.getColumnIndexOrThrow("id")
            val dayCol=c.getColumnIndexOrThrow("dia_entrega")
            val location=c.getColumnIndexOrThrow("endereco_referencia")
            val value=c.getColumnIndexOrThrow("valor_compra_centavos")
            val initial=c.getColumnIndexOrThrow("pagamento_inicial")
            val finalCol=c.getColumnIndexOrThrow("resultado_pagamento")
            val form=c.getColumnIndexOrThrow("forma_pagamento")
            val tip=c.getColumnIndexOrThrow("caixinha_centavos")
            val obs=c.getColumnIndexOrThrow("observacao")
            val done=c.getColumnIndexOrThrow("realizada")
            while(c.moveToNext()) {
                result.add(MainActivity.Delivery(
                    id=c.getString(id),
                    day=c.getString(dayCol),
                    location=c.getString(location) ?: "",
                    initialPayment=c.getString(initial) ?: "",
                    purchaseValue=formatCents(c.getLong(value)),
                    finalPayment=c.getString(finalCol),
                    paymentMethod=c.getString(form),
                    tip=formatCents(c.getLong(tip)),
                    observation=c.getString(obs) ?: "",
                    completed=c.getInt(done)==1
                ))
            }
        }
        return result
    }

    private fun parseMoneyToCents(value: String): Long? {
        val clean=value.replace("R$","").replace(".","").replace(",",".").trim()
        if(clean.isBlank()) return null
        return clean.toDoubleOrNull()?.let{(it*100.0).toLong()}
    }

    private fun formatCents(value: Long): String {
        if(value<=0L) return ""
        return "R$ %.2f".format(java.util.Locale("pt","BR"), value/100.0)
    }

    private fun now(): String = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date())

    companion object {
        private const val DATABASE_NAME="sistema_entregas.db"
        private const val DATABASE_VERSION=2
    }
}
