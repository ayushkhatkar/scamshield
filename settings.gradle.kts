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

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "ScamShield"

// :engine is a pure-Kotlin (JVM) library with all detection logic.
// It has no Android dependencies, so it can be unit-tested on the JVM
// and later published as the paid "ScamShield SDK" for banks/fintechs.
include(":engine")
include(":app")
