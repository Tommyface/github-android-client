plugins {
    id("gy.android.lib")
    id("gy.compose")
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.feature.auth.domain)
    implementation(projects.core.presentation)
    implementation(libs.bundles.compose.ui)
    implementation(libs.koin.androidx.compose)
    implementation(libs.bundles.koin.common)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
}
