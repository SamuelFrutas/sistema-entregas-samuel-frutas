package br.com.samuelfrutas.entregas.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

data class EntregaLocal(
    val id: Long = 0, val dia: String, val predio: String, val bloco: String,
    val apartamento: String, val semEndereco: Boolean, val enderecoReferencia: String,
    val valorCompraCentavos: Long?, val pagamentoInicial: String,
    val resultadoPagamento: String = "", val formaPagamento: String = "",
    val caixinhaCentavos: Long = 0, val observacao: String = "",
    val realizada: Boolean = false, val sincronizacao: String = "PENDENTE"
)

class EntregaDbHelper(context: Context) : SQLiteOpenHelper(context, "entregas.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""CREATE TABLE entregas (
            id INTEGER PRIMARY KEY AUTOINCREMENT, dia TEXT NOT NULL,
            predio TEXT NOT NULL, bloco TEXT NOT NULL, apartamento TEXT NOT NULL,
            sem_endereco INTEGER NOT NULL DEFAULT 0, endereco_referencia TEXT NOT NULL DEFAULT '',
            valor_compra_centavos INTEGER, pagamento_inicial TEXT NOT NULL,
            resultado_pagamento TEXT NOT NULL DEFAULT '', forma_pagamento TEXT NOT NULL DEFAULT '',
            caixinha_centavos INTEGER NOT NULL DEFAULT 0, observacao TEXT NOT NULL DEFAULT '',
            realizada INTEGER NOT NULL DEFAULT 0, sincronizacao TEXT NOT NULL DEFAULT 'PENDENTE'
        )""")
        db.execSQL("CREATE INDEX idx_entregas_dia ON entregas(dia)")
        db.execSQL("""CREATE TABLE exclusoes_pendentes (
            id INTEGER PRIMARY KEY,
            criado_em INTEGER NOT NULL
        )""")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("""CREATE TABLE IF NOT EXISTS exclusoes_pendentes (
                id INTEGER PRIMARY KEY,
                criado_em INTEGER NOT NULL
            )""")
        }
    }
    fun inserir(e: EntregaLocal): Long {
        val v = ContentValues().apply {
            put("dia",e.dia);put("predio",e.predio);put("bloco",e.bloco);put("apartamento",e.apartamento)
            put("sem_endereco",if(e.semEndereco)1 else 0);put("endereco_referencia",e.enderecoReferencia)
            if(e.valorCompraCentavos==null)putNull("valor_compra_centavos") else put("valor_compra_centavos",e.valorCompraCentavos)
            put("pagamento_inicial",e.pagamentoInicial);put("observacao",e.observacao)
        }
        return writableDatabase.insertOrThrow("entregas",null,v)
    }
    fun atualizarLocal(e: EntregaLocal) {
        val v = ContentValues().apply {
            put("predio", e.predio)
            put("bloco", e.bloco)
            put("apartamento", e.apartamento)
            put("sem_endereco", if (e.semEndereco) 1 else 0)
            put("endereco_referencia", e.enderecoReferencia)
            if (e.valorCompraCentavos == null) putNull("valor_compra_centavos")
            else put("valor_compra_centavos", e.valorCompraCentavos)
            put("pagamento_inicial", e.pagamentoInicial)
            put("sincronizacao", "PENDENTE")
        }
        writableDatabase.update("entregas", v, "id=?", arrayOf(e.id.toString()))
    }

    fun substituirDoServidor(e: EntregaLocal) {
        val v = ContentValues().apply {
            put("dia", e.dia)
            put("predio", e.predio)
            put("bloco", e.bloco)
            put("apartamento", e.apartamento)
            put("sem_endereco", if (e.semEndereco) 1 else 0)
            put("endereco_referencia", e.enderecoReferencia)
            if (e.valorCompraCentavos == null) putNull("valor_compra_centavos")
            else put("valor_compra_centavos", e.valorCompraCentavos)
            put("pagamento_inicial", e.pagamentoInicial)
            put("resultado_pagamento", e.resultadoPagamento)
            put("forma_pagamento", e.formaPagamento)
            put("caixinha_centavos", e.caixinhaCentavos)
            put("observacao", e.observacao)
            put("realizada", if (e.realizada) 1 else 0)
            put("sincronizacao", "SINCRONIZADA")
        }
        writableDatabase.update("entregas", v, "id=?", arrayOf(e.id.toString()))
    }

    fun marcarSincronizada(id: Long) {
        val v = ContentValues().apply { put("sincronizacao", "SINCRONIZADA") }
        writableDatabase.update("entregas", v, "id=?", arrayOf(id.toString()))
    }

    fun marcarSincronizacaoPendente(id: Long) {
        val v = ContentValues().apply { put("sincronizacao", "PENDENTE") }
        writableDatabase.update("entregas", v, "id=?", arrayOf(id.toString()))
    }

    fun excluir(id: Long) {
        writableDatabase.delete("entregas", "id=?", arrayOf(id.toString()))
    }

    fun excluirLocalPendente(id: Long) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("entregas", "id=?", arrayOf(id.toString()))
            val tombstone = ContentValues().apply {
                put("id", id)
                put("criado_em", System.currentTimeMillis())
            }
            db.insertWithOnConflict("exclusoes_pendentes", null, tombstone, SQLiteDatabase.CONFLICT_REPLACE)
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun temExclusaoPendente(id: Long): Boolean =
        readableDatabase.query(
            "exclusoes_pendentes",
            arrayOf("id"),
            "id=?",
            arrayOf(id.toString()),
            null, null, null, "1"
        ).use { it.moveToFirst() }

    fun listarExclusoesPendentes(): List<Long> {
        val out = mutableListOf<Long>()
        readableDatabase.query("exclusoes_pendentes", arrayOf("id"), null, null, null, null, "criado_em ASC").use { c ->
            while (c.moveToNext()) out += c.getLong(c.getColumnIndexOrThrow("id"))
        }
        return out
    }

    fun limparExclusaoPendente(id: Long) {
        writableDatabase.delete("exclusoes_pendentes", "id=?", arrayOf(id.toString()))
    }

    fun sincronizacaoPendente(id: Long): Boolean =
        readableDatabase.query(
            "entregas",
            arrayOf("sincronizacao"),
            "id=?",
            arrayOf(id.toString()),
            null, null, null, "1"
        ).use { c ->
            c.moveToFirst() && c.getString(c.getColumnIndexOrThrow("sincronizacao")) == "PENDENTE"
        }
    
    fun sincronizarDoFirestore(e: EntregaLocal) {
        val v = ContentValues().apply {
            put("dia", e.dia)
            put("predio", e.predio)
            put("bloco", e.bloco)
            put("apartamento", e.apartamento)
            put("sem_endereco", if (e.semEndereco) 1 else 0)
            put("endereco_referencia", e.enderecoReferencia)
            if (e.valorCompraCentavos == null) putNull("valor_compra_centavos")
            else put("valor_compra_centavos", e.valorCompraCentavos)
            put("pagamento_inicial", e.pagamentoInicial)
            put("resultado_pagamento", e.resultadoPagamento)
            put("forma_pagamento", e.formaPagamento)
            put("caixinha_centavos", e.caixinhaCentavos)
            put("observacao", e.observacao)
            put("realizada", if (e.realizada) 1 else 0)
            put("sincronizacao", "SINCRONIZADA")
        }
        val updated = writableDatabase.update("entregas", v, "id=?", arrayOf(e.id.toString()))
        if (updated == 0) {
            v.put("id", e.id)
            writableDatabase.insertOrThrow("entregas", null, v)
        }
    }


    fun atualizarFinal(id:Long,resultado:String,forma:String,caixinha:Long,observacao:String){
        val v=ContentValues().apply{put("resultado_pagamento",resultado);put("forma_pagamento",forma);put("caixinha_centavos",caixinha);put("observacao",observacao);put("realizada",1)}
        writableDatabase.update("entregas",v,"id=?",arrayOf(id.toString()))
    }
    fun listarDia(dia:String):List<EntregaLocal>{
        val out=mutableListOf<EntregaLocal>()
        readableDatabase.query("entregas",null,"dia=?",arrayOf(dia),null,null,"realizada ASC,id ASC").use{c->
            while(c.moveToNext())out+=EntregaLocal(
                id=c.getLong(c.getColumnIndexOrThrow("id")),dia=c.getString(c.getColumnIndexOrThrow("dia")),
                predio=c.getString(c.getColumnIndexOrThrow("predio")),bloco=c.getString(c.getColumnIndexOrThrow("bloco")),
                apartamento=c.getString(c.getColumnIndexOrThrow("apartamento")),semEndereco=c.getInt(c.getColumnIndexOrThrow("sem_endereco"))==1,
                enderecoReferencia=c.getString(c.getColumnIndexOrThrow("endereco_referencia")),
                valorCompraCentavos=if(c.isNull(c.getColumnIndexOrThrow("valor_compra_centavos")))null else c.getLong(c.getColumnIndexOrThrow("valor_compra_centavos")),
                pagamentoInicial=c.getString(c.getColumnIndexOrThrow("pagamento_inicial")),
                resultadoPagamento=c.getString(c.getColumnIndexOrThrow("resultado_pagamento")),
                formaPagamento=c.getString(c.getColumnIndexOrThrow("forma_pagamento")),
                caixinhaCentavos=c.getLong(c.getColumnIndexOrThrow("caixinha_centavos")),
                observacao=c.getString(c.getColumnIndexOrThrow("observacao")),
                realizada=c.getInt(c.getColumnIndexOrThrow("realizada"))==1,
                sincronizacao=c.getString(c.getColumnIndexOrThrow("sincronizacao")))
        }
        return out
    }
}
