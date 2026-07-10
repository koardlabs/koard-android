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
        // Koard Android SDK - published to GitHub Packages (Maven).
        // GitHub Packages requires auth even for public reads: put a token with
        // read:packages in ~/.gradle/gradle.properties as gpr.user / gpr.key
        // (or set GITHUB_ACTOR / GITHUB_TOKEN in the environment).
        maven {
            url = uri("https://maven.pkg.github.com/koardlabs/koard-android")
            credentials {
                username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
                password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
            }
            content { includeGroup("com.koardlabs") }
        }
    }
}

rootProject.name = "Koard Demo"
