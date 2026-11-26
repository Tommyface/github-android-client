dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        // Maven Central 的 Google 官方镜像：GitHub 共享 runner IP 池常被 Central 直连限流（429），
        // 优先走镜像可消除瞬时失败；镜像内容与 Central 一致。
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        gradlePluginPortal()
    }
}

rootProject.name = "build-logic"
include(":convention")
