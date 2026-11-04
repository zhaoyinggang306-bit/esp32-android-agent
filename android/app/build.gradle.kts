plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.handsy.androidagent"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.handsy.androidagent"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}
