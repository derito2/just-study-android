plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// PoC 4 (phase 9 plan §2): a separate app to measure on-device Korean speech recognition. Never merged.
android {
    namespace = "com.dero.juststudykorean.speechpoc"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    defaultConfig {
        applicationId = "com.dero.juststudykorean"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "poc4"
        ndk { abiFilters += "arm64-v8a" }
    }
    buildTypes {
        debug { applicationIdSuffix = ".speechpoc" }
    }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.mlkit.genai.speech.recognition)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
}
