plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "au.com.greektv"
    compileSdk = 35
    defaultConfig {
        applicationId = "au.com.greektv"
        minSdk = 23
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }
}
