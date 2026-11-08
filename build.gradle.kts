// AGP 9's built-in Kotlin is 2.2; this pins KGP to the catalog's Kotlin (2.4.0, see libs.versions.toml).
buildscript {
    dependencies {
        classpath(libs.kotlin.gradle.plugin)
    }
}
// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.roborazzi) apply false
}
