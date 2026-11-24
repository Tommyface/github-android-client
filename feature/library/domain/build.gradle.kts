plugins {
    id("gy.kotlin.lib")
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.feature.discover.domain)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}
