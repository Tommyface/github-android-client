plugins {
    id("gy.kotlin.lib")
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
