pluginManagement {
    includeBuild("build-logic")
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
        maven("https://jitpack.io") // KSafe
    }
}

rootProject.name = "GithubAndroidClient"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:domain")
include(":core:data")
include(":core:presentation")

val features = listOf(
    "auth", "repo", "discover", "release", "download", "share", "search", "starred", "profile"
)
features.forEach { name ->
    if (name == "share") {
        include(":feature:share:presentation")
    } else {
        listOf("domain", "data", "presentation").forEach { layer ->
            include(":feature:$name:$layer")
        }
    }
}

include(":konsist-test")
