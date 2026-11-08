// PoC 2: genai-prompt 1.0.0-beta5 is built with Kotlin 2.4; AGP 9.5's built-in Kotlin is 2.2.
buildscript {
    dependencies {
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")
    }
}
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
}