import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Pure Kotlin/JVM: rules ported from the web (backend/app), checked against golden/.
plugins {
    id("org.jetbrains.kotlin.jvm") // version from the buildscript classpath (libs.kotlin.gradle.plugin)
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_11 }
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.org.json)
}
