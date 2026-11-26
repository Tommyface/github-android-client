plugins {
    id("gy.android.app")
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.gy.githubclient"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        // GitHub OAuth App client_id（公开信息，Device Flow 无需 client_secret）。
        buildConfigField("String", "OAUTH_CLIENT_ID", "\"Ov23li50RuIKE1lG7qe4\"")
    }
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.presentation)
    implementation(projects.platform.diagnostics)

    implementation(projects.feature.auth.domain)
    implementation(projects.feature.auth.data)
    implementation(projects.feature.auth.presentation)
    implementation(projects.feature.repo.domain)
    implementation(projects.feature.repo.data)
    implementation(projects.feature.repo.presentation)
    implementation(projects.feature.discover.domain)
    implementation(projects.feature.discover.data)
    implementation(projects.feature.discover.presentation)
    implementation(projects.feature.download.domain)
    implementation(projects.feature.download.data)
    implementation(projects.feature.download.presentation)
    implementation(projects.feature.search.domain)
    implementation(projects.feature.search.data)
    implementation(projects.feature.search.presentation)
    implementation(projects.feature.starred.domain)
    implementation(projects.feature.starred.data)
    implementation(projects.feature.starred.presentation)
    implementation(projects.feature.profile.domain)
    implementation(projects.feature.profile.data)
    implementation(projects.feature.profile.presentation)
    implementation(projects.feature.library.domain)
    implementation(projects.feature.library.data)
    implementation(projects.feature.library.presentation)

    implementation(libs.bundles.ktor.common)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.bundles.koin.common)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.bundles.compose.ui)
    implementation(libs.core.ktx)
    implementation(libs.work.runtime.ktx)

    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
