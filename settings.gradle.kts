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
        // Use the exact checked-in release, including its POM/runtime dependencies.
        exclusiveContent {
            forRepository {
                maven {
                    name = "KoardReleaseMirror"
                    url = uri("libs-maven")
                }
            }
            filter { includeVersion("com.koard", "koard-android-sdk", "1.0.7") }
        }
    }
}

rootProject.name = "Koard Demo"
