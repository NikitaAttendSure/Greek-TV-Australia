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
        versionCode = 13
        versionName = "1.0.2"
    }
    signingConfigs {
        create("reskakisRelease") {
            val path = System.getenv("RESKAKIS_KEYSTORE_PATH")
            if (!path.isNullOrBlank()) storeFile = file(path)
            storePassword = System.getenv("RESKAKIS_KEYSTORE_PASSWORD")
            keyAlias = "reskakis-tv"
            keyPassword = System.getenv("RESKAKIS_KEYSTORE_PASSWORD")
        }
        create("papasRelease") {
            val path = System.getenv("PAPAS_KEYSTORE_PATH")
            if (!path.isNullOrBlank()) storeFile = file(path)
            storePassword = System.getenv("PAPAS_KEYSTORE_PASSWORD")
            keyAlias = "papas-tv"
            keyPassword = System.getenv("PAPAS_KEYSTORE_PASSWORD")
        }
    }
    flavorDimensions += "brand"
    productFlavors {
        create("reskakis") {
            dimension = "brand"
            applicationId = "au.com.greektv"
            resValue("string", "app_name", "GREEK ONE")
            signingConfig = signingConfigs.getByName("reskakisRelease")
        }
        create("pappas") {
            dimension = "brand"
            applicationId = "au.com.pappastv"
            versionCode = 8
            versionName = "1.5.1"
            resValue("string", "app_name", "PAPAS TV")
            signingConfig = signingConfigs.getByName("papasRelease")
        }
    }
    buildTypes {
        getByName("release") { isMinifyEnabled = false }
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
