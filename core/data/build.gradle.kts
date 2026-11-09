// Room, DataStore and the backup (plan, phase 2). Android library with Hilt.
plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room3)
}

android {
    namespace = "com.dero.juststudykorean.data"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { it.jvmArgs("--add-opens=java.base/java.io=ALL-UNNAMED", "--add-exports=java.base/jdk.internal.access=ALL-UNNAMED") } // Robolectric on new JDKs
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// The db goldens run on the JVM (Robolectric) and on the phone (androidTest: Android's own regex and SQLite).
android.sourceSets {
    getByName("test").kotlin.srcDir("src/sharedTest/java")
    getByName("androidTest").kotlin.srcDir("src/sharedTest/java")
    getByName("androidTest").assets.srcDir("../../golden/db") // only the synthetic db goldens, never golden/private
    getByName("test").assets.srcDir("schemas") // MigrationTest reads the exported schemas
}

room3 {
    schemaDirectory("$projectDir/schemas") // the v1 schema JSON is committed
}

dependencies {
    api(project(":core:korean")) // the app shows Rating, Comparison… from the repositories
    api(project(":core:pdf")) // the pipeline parses PDFs with MuPDF (its types are in DocumentIndexer)
    implementation(libs.room3.runtime)
    implementation(libs.androidx.sqlite.bundled)
    ksp(libs.room3.compiler)
    implementation(libs.kotlinx.serialization.json) // backup rows as JsonElement (Python json.dumps round trip)
    api(libs.androidx.datastore.preferences)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    // Repositories against the golden database: Robolectric + Room in memory on Android's SQLite driver.
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.sqlite.framework)
    testImplementation(libs.room3.testing)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.sqlite.framework)
    androidTestCompileOnly(libs.robolectric) // @Config on the shared golden tests; ignored on the phone
}
