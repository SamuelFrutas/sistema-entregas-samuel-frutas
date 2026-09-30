package br.com.samuelfrutas.entregas.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class EntregaDbHelper(context: Context) : SQLiteOpenHelper(
    context,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
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

        db.execSQL(
            "CREATE INDEX idx_entregas_dia ON entregas(dia_entrega)"
        )
        db.execSQL(
            "CREATE INDEX idx_entregas_sync ON entregas(sincronizacao_status)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migrações serão adicionadas somente quando uma alteração de esquema for aprovada.
    }

    companion object {
        private const val DATABASE_NAME = "sistema_entregas.db"
        private const val DATABASE_VERSION = 1
    }
}
