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
        versionCode = 1
        versionName = "0.1.0"
    }
}

kotlin {
    jvmToolchain(17)
}

// Mantém a alteração isolada na tela de entregas realizadas do APK.
// O patch é aplicado somente durante o build, sem mexer no fluxo de cadastro, pagamento ou sincronização.
val patchCompletedCard = tasks.register("patchCompletedCard") {
    doLast {
        val source = file("src/main/java/br/com/samuelfrutas/entregas/MainActivity.kt")
        var text = source.readText()

        if (!text.contains("private fun addCompletedExtras")) {
            val functionMarker = "    private fun makeCompletedCard(e: EntregaLocal): View {"
            val helper = """
    private fun addCompletedExtras(box: LinearLayout, e: EntregaLocal) {
        if (e.caixinhaCentavos > 0) {
            box.addView(txt("Caixinha: " + centsText(e.caixinhaCentavos), 12f, green, true))
        }
        val observation = e.observacao.trim()
        if (observation.isNotBlank()) {
            box.addView(txt("⚠️ Observação: " + observation, 12f, Color.rgb(255, 213, 74), true))
        }
    }

"""
            check(functionMarker in text) { "Não foi possível localizar makeCompletedCard para aplicar o ajuste." }
            text = text.replace(functionMarker, helper + functionMarker)
        }

        if (!text.contains("addCompletedExtras(box, e)")) {
            val returnMarker = "        box.addView(body)\n        return box.apply {"
            check(returnMarker in text) { "Não foi possível localizar o final de makeCompletedCard para aplicar o ajuste." }
            text = text.replace(returnMarker, "        box.addView(body)\n        addCompletedExtras(box, e)\n        return box.apply {")
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
