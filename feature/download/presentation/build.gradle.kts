plugins {
    id("gy.android.lib")
    id("gy.compose")
}

dependencies {
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.datetime)
    implementation(projects.core.domain)
    implementation(projects.core.presentation)
    implementation(projects.feature.download.domain)
    implementation(projects.platform.apkinstall)
    implementation(libs.bundles.compose.ui)
    implementation(libs.koin.androidx.compose)
    implementation(libs.bundles.koin.common)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
}
