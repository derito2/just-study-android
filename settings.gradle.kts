pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven("https://maven.ghostscript.com") { // MuPDF (DECISIONS: 1.28.5, never bumped without golden/parser_raw)
            content { includeGroup("com.artifex.mupdf") }
        }
    }
}

rootProject.name = "JustStudyKorean"
include(":app")
include(":core:data")
include(":core:korean")
include(":core:pdf")
