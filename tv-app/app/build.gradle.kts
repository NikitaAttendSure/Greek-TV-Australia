plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "au.com.greektv"
    compileSdk = 36
    defaultConfig {
        applicationId = "au.com.greektv"
        minSdk = 23
        targetSdk = 36
        versionCode = 5
        versionName = "0.5.0"
    }
    flavorDimensions += "brand"
    productFlavors {
        create("reskakis") {
            dimension = "brand"
            applicationId = "au.com.greektv"
            resValue("string", "app_name", "RESKAKIS TV")
        }
        create("pappas") {
            dimension = "brand"
            applicationId = "au.com.pappastv"
            versionCode = 1
            versionName = "1.0.0"
            resValue("string", "app_name", "PAPAS TV")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    bundle {
        language { enableSplit = false }
    }
}
dependencies {
    val media3Version = "1.11.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
}
