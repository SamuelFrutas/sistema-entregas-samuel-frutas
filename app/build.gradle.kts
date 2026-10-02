plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")\n    id("com.google.gms.google-services")
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


dependencies {
    implementation(platform("com.google.firebase:firebase-bom:33.16.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
}
