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
        versionCode = 2
        versionName = "0.2.0"
    }
}
dependencies {
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-exoplayer-hls:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
}
