plugins {
    id("gy.kotlin.lib")
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.mutation)
    implementation(projects.core.permission)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    testImplementation(libs.kotlinx.coroutines.test)
}
