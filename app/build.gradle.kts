import java.time.Duration

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.dero.juststudykorean"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }

    defaultConfig {
        applicationId = "com.dero.juststudykorean"
        minSdk = 31
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        // Only the OPPO (arm64): the other ABIs of MuPDF and SQLite were 32 MB of the APK.
        ndk { abiFilters += "arm64-v8a" }
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true // Robolectric/Roborazzi render with the real resources and fonts
        unitTests.all { it.jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED") } // Robolectric's FileDescriptor access on new JDKs
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":core:data"))
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.navigation3.runtime)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.navigation3) // a ViewModel per screen, cleared when it leaves the stack
    testImplementation(libs.junit)
    // Roborazzi: roborazzi-compose registers its own activity in Robolectric, so no ui-test-manifest (it would add an exported activity).
    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.room3.runtime) // behaviour tests: the real repositories on the golden base, in memory
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation(libs.org.json) // TokensTest reads design/tokens.json (android.jar's org.json is a stub)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

// Every unit test is printed when it starts and ends, so a test that hangs has a name in the CI log; and the task
// gives up after 20 minutes (it takes about 6) instead of running until the job's limit.
tasks.withType<Test>().configureEach {
    testLogging { events("started", "passed", "failed", "skipped") }
    timeout.set(Duration.ofMinutes(20))
}
