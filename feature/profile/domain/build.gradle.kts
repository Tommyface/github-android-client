plugins {
    id("gy.kotlin.lib")
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.mutation)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
}
