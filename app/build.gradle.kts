plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "br.com.samuelfrutas.entregas"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.samuelfrutas.entregas"
        minSdk = 24
        targetSdk = 35
        versionCode = 4
        versionName = "0.1.3"
    }
}

kotlin {
    jvmToolchain(17)
}

// Ajustes das telas do APK aplicados durante o build, usando os trechos reais de MainActivity.kt.
val patchCompletedCard = tasks.register("patchCompletedCard") {
    doLast {
        val source = file("src/main/java/br/com/samuelfrutas/entregas/MainActivity.kt")
        var text = source.readText()

        if (!text.contains("private fun addCompletedExtras")) {
            val functionMarker = "    private fun makeCompletedCard(e: EntregaLocal): View {"
            val helper = """
    private fun addCompletedExtras(box: LinearLayout, e: EntregaLocal) {
        if (e.caixinhaCentavos > 0) {
            box.addView(txt("Caixinha: " + centsText(e.caixinhaCentavos), 13f, green, true))
        }
        val observation = e.observacao.trim()
        if (observation.isNotBlank()) {
            box.addView(txt("⚠️ Observação: " + observation, 13f, Color.rgb(255, 213, 74), true))
        }
    }

"""
            check(text.contains(functionMarker)) { "Não foi possível localizar makeCompletedCard." }
            text = text.replace(functionMarker, helper + functionMarker)
        }

        if (!text.contains("addCompletedExtras(box, e)")) {
            val returnMarker = "        box.addView(txt(detail, 12f, if (result == \"Pago\") green else Color.rgb(240, 70, 75), true))\n        return box"
            check(text.contains(returnMarker)) { "Não foi possível localizar o final real de makeCompletedCard." }
            text = text.replace(returnMarker, "        box.addView(txt(detail, 12f, if (result == \"Pago\") green else Color.rgb(240, 70, 75), true))\n        addCompletedExtras(box, e)\n        return box")
        }

        if (!text.contains("CONTADORES_RESUMO_MAIORES")) {
            val pendingMarker = "        val pending = iconTitle(R.drawable.ic_pending, \"PENDENTES\", pendingCount.toString())"
            val pendingReplacement = """        val pending = iconTitle(R.drawable.ic_pending, "PENDENTES", pendingCount.toString()).apply {
            // CONTADORES_RESUMO_MAIORES
            val texts = getChildAt(1) as? LinearLayout
            (texts?.getChildAt(1) as? TextView)?.apply {
                textSize = 25f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(10), dp(2), dp(2), dp(2))
            }
        }"""
            check(text.contains(pendingMarker)) { "Não foi possível localizar o contador PENDENTES." }
            text = text.replace(pendingMarker, pendingReplacement)

            val doneMarker = "        val done = iconTitle(R.drawable.ic_check_circle, \"REALIZADAS\", doneCount.toString())"
            val doneReplacement = """        val done = iconTitle(R.drawable.ic_check_circle, "REALIZADAS", doneCount.toString()).apply {
            val texts = getChildAt(1) as? LinearLayout
            (texts?.getChildAt(1) as? TextView)?.apply {
                textSize = 25f
                setTextColor(Color.WHITE)
                setTypeface(null, Typeface.BOLD)
                setPadding(dp(10), dp(2), dp(2), dp(2))
            }
        }"""
            check(text.contains(doneMarker)) { "Não foi possível localizar o contador REALIZADAS." }
            text = text.replace(doneMarker, doneReplacement)
        }

        // Mais respiro no topo das telas internas para afastar o título do status bar.
        text = text.replace("setPadding(dp(16), dp(8), dp(16), dp(20))", "setPadding(dp(16), dp(30), dp(16), dp(20))")
        text = text.replace("setPadding(0, dp(4), 0, dp(10))", "setPadding(0, dp(10), 0, dp(10))")

        if (!text.contains("OBSERVACAO_ENTREGA_NOVA_V1")) {
            val newFieldMarker = "        val ref = field(\"Endereço / referência *\")\n"
            val newFieldReplacement = """        val ref = field("Endereço / referência *")
        // OBSERVACAO_ENTREGA_NOVA_V1
        val obsEntrega = field("Observação da entrega (opcional)").apply {
            minHeight = dp(54)
            setPadding(dp(14), dp(4), dp(14), dp(4))
            background = rounded(Color.rgb(8, 34, 49), 14f, Color.rgb(55, 105, 126))
            setOnFocusChangeListener { _, focused ->
                background = rounded(Color.rgb(8, 34, 49), 14f, if (focused) green else Color.rgb(55, 105, 126))
            }
        }
"""
            check(text.contains(newFieldMarker)) { "Não foi possível localizar o campo de referência da nova entrega." }
            text = text.replace(newFieldMarker, newFieldReplacement)

            val addRefMarker = "        add(row1); add(semBloco, 2); add(alphaBlock, 0); add(noAddress, 6); add(ref)"
            val addRefReplacement = "        add(row1); add(semBloco, 2); add(alphaBlock, 0); add(noAddress, 6); add(ref, 6); add(obsEntrega, 10)"
            check(text.contains(addRefMarker)) { "Não foi possível localizar os campos da nova entrega." }
            text = text.replace(addRefMarker, addRefReplacement)

            val currentMarker = "                enderecoReferencia = ref.text.toString(),\n                valorCompraCentavos = null,"
            val currentReplacement = "                enderecoReferencia = ref.text.toString(),\n                observacaoEntrega = obsEntrega.text.toString().trim(),\n                valorCompraCentavos = null,"
            check(text.contains(currentMarker)) { "Não foi possível salvar a observação da nova entrega." }
            text = text.replace(currentMarker, currentReplacement)

            val reviewMarker = "        add(card(\"⌖  \" + address, 15f), 6)\n"
            val reviewReplacement = """        add(card("⌖  " + address, 15f), 6)
        if (e.observacaoEntrega.isNotBlank()) {
            add(card("⚠️  Observação da entrega: " + e.observacaoEntrega, 13f, Color.rgb(45, 50, 35)), 8)
        }
"""
            check(text.contains(reviewMarker)) { "Não foi possível mostrar a observação na revisão." }
            text = text.replace(reviewMarker, reviewReplacement)

            val pendingInfoMarker = "        info.addView(purchase)\n        body.addView(info)"
            val pendingInfoReplacement = """        info.addView(purchase)
        if (e.observacaoEntrega.isNotBlank()) {
            info.addView(txt("⚠️  " + e.observacaoEntrega, 12.5f, Color.rgb(255, 213, 74), true).apply {
                setPadding(dp(2), dp(8), dp(2), dp(2))
            })
        }
        body.addView(info)"""
            check(text.contains(pendingInfoMarker)) { "Não foi possível colocar a observação no card de pendentes." }
            text = text.replace(pendingInfoMarker, pendingInfoReplacement)

            val editFieldMarker = "        val ref = field(\"Endereço / referência *\").apply { setText(e.enderecoReferencia) }\n"
            val editFieldReplacement = """        val ref = field("Endereço / referência *").apply { setText(e.enderecoReferencia) }
        val obsEntrega = field("Observação da entrega (opcional)").apply { setText(e.observacaoEntrega) }
"""
            check(text.contains(editFieldMarker)) { "Não foi possível localizar a referência da edição." }
            text = text.replace(editFieldMarker, editFieldReplacement)

            val editAddMarker = "        add(semBloco, 4); add(noAddress, 4); add(ref, 6)"
            val editAddReplacement = "        add(semBloco, 4); add(noAddress, 4); add(ref, 6); add(obsEntrega, 6)"
            check(text.contains(editAddMarker)) { "Não foi possível adicionar a observação na edição." }
            text = text.replace(editAddMarker, editAddReplacement)

            val editCopyMarker = "                enderecoReferencia = ref.text.toString(),\n                valorCompraCentavos = cents,"
            val editCopyReplacement = "                enderecoReferencia = ref.text.toString(),\n                observacaoEntrega = obsEntrega.text.toString().trim(),\n                valorCompraCentavos = cents,"
            check(text.contains(editCopyMarker)) { "Não foi possível salvar a observação na edição." }
            text = text.replace(editCopyMarker, editCopyReplacement)
        }

        check(text.contains("observacaoEntrega = obsEntrega.text.toString().trim()")) { "Campo de observação da entrega não foi aplicado." }
        check(text.contains("e.observacaoEntrega.isNotBlank()")) { "Observação da entrega não foi exibida nos pendentes." }
        source.writeText(text)
    }
}

tasks.named("preBuild") {
    dependsOn(patchCompletedCard)
}

dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")
}