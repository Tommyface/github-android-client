plugins {
    `kotlin-dsl`
}

repositories {
    google()
            // Maven Central 的 Google 官方镜像：GitHub 共享 runner IP 池常被 Central 直连限流（429），
        // 优先走镜像可消除瞬时失败；镜像内容与 Central 一致。
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("com.android.tools.build:gradle:8.7.3")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:2.0.21")
    implementation("io.gitlab.arturbosch.detekt:detekt-gradle-plugin:1.23.7")
    implementation("com.diffplug.spotless:spotless-plugin-gradle:6.25.0")
    implementation("org.jetbrains.kotlinx:kover-gradle-plugin:0.9.1")
}
