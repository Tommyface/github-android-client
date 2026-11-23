plugins {
    id("gy.android.lib")
    id("gy.compose")
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.bundles.compose.ui)
    implementation(libs.koin.androidx.compose)
    implementation(libs.bundles.koin.common)
}
