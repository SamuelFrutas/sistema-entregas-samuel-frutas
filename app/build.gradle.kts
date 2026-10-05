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
        versionCode = 3
        versionName = "0.1.2"
    }
}

kotlin {
    jvmToolchain(17)
}

// Ajustes das telas do APK aplicados no código-fonte durante o build.
// A transformação é idempotente e usa os trechos reais de MainActivity.kt.
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
            text = text.replace(
                returnMarker,
                "        box.addView(txt(detail, 12f, if (result == \"Pago\") green else Color.rgb(240, 70, 75), true))\n        addCompletedExtras(box, e)\n        return box"
            )
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
