plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "br.com.samuelfrutas.entregas"
    compileSdk = 35

    defaultConfig {
        applicationId = "br.com.samuelfrutas.entregas"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}

kotlin {
    jvmToolchain(17)
}
