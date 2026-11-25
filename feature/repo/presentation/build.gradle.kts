plugins {
    id("gy.android.lib")
    id("gy.compose")
}

dependencies {
    testImplementation(libs.junit)
    implementation(projects.core.domain)
    implementation(projects.core.presentation)
    implementation(projects.feature.repo.domain)
    implementation(projects.core.permission)
    implementation(projects.core.mutation)
    implementation(projects.feature.download.domain)
    implementation(libs.bundles.compose.ui)
    implementation(libs.coil.compose)
    implementation(libs.koin.androidx.compose)
    implementation(libs.bundles.koin.common)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
}
