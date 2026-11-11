// The web's rag/parser.py on MuPDF (plan, phase 4): text, headings, sections and ruled tables of a digital PDF.
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.dero.juststudykorean.pdf"
    compileSdk {
        version = release(37) { minorApiLevel = 2 }
    }
    defaultConfig {
        minSdk = 31
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// MuPDF is native, so the goldens run on the phone. golden/parser and golden/parser_raw share file names: they are
// copied side by side into one assets folder (never golden/private).
val goldenAssets = layout.buildDirectory.dir("golden-assets").get().asFile
val syncGolden = tasks.register<Sync>("syncGoldenAssets") {
    from(rootDir.resolve("golden")) { include("parser/**", "parser_raw/**") }
    into(goldenAssets)
}
android.sourceSets.getByName("androidTest").assets.srcDir(goldenAssets)
tasks.matching { it.name.startsWith("merge") && it.name.endsWith("AndroidTestAssets") }.configureEach { dependsOn(syncGolden) }

dependencies {
    implementation(project(":core:korean")) // Python string semantics (isspace, strip, split, len)
    implementation(libs.mupdf.fitz)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner) // org.json comes with Android
}
