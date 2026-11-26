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
    }
}

rootProject.name = "GithubAndroidClient"
enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:domain")
include(":core:data")
include(":core:presentation")

// feature 清单按域展开三层（domain / data / presentation）。
// 约定：模块一旦纳入本清单，必须有真实源码；空壳模块一律不在此登记，
// 需要时随功能实现一并创建（见 spec/parity/PARITY-NOTICE.md「空壳模块处置」）。
val features = listOf(
    "auth", "repo", "discover", "download", "search", "starred", "profile", "library",
)
features.forEach { name ->
    listOf("domain", "data", "presentation").forEach { layer ->
        include(":feature:$name:$layer")
    }
}

include(":konsist-test")
