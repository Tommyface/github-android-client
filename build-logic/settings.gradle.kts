// build-logic 是独立的 included build，**有自己的**仓库解析逻辑。
// 注意：只配 dependencyResolutionManagement 不够——`kotlin-dsl` 等插件的解析走
// pluginManagement，其传递依赖（如 kotlin-stdlib）默认从 plugins.gradle.org 回落到
// repo.maven.apache.org，在 GitHub 共享 runner 上极易命中 429 Too Many Requests。
// 因此这里必须同时配置 pluginManagement，并把 Maven Central 的 Google 官方镜像放在前面。
pluginManagement {
    repositories {
        google()
                // Maven Central 的 Google 官方镜像：GitHub 共享 runner IP 池常被 Central 直连限流（429），
        // 优先走镜像可消除瞬时失败；镜像内容与 Central 一致。
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
                // Maven Central 的 Google 官方镜像：优先于直连 Central，规避 429 限流
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
mavenCentral()
        gradlePluginPortal()
    }
}

rootProject.name = "build-logic"
include(":convention")
