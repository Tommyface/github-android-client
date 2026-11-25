plugins {
    id("gy.kotlin.lib")
}

dependencies {
    implementation(projects.core.permission)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
