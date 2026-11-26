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
                // Maven Central 的 Google 官方镜像：GitHub 共享 runner IP 池常被 Central 直连限流（429），
        // 优先走镜像可消除瞬时失败；镜像内容与 Central 一致。
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
                // Maven Central 的 Google 官方镜像：优先于直连 Central，规避 429 限流
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
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

// platform/*：系统能力层（只放真正需要 Android API 的部分）。
// 约定：不得依赖 feature/*（konsist 规则 3）。
include(":platform:apkinstall")
include(":platform:diagnostics")

include(":konsist-test")
