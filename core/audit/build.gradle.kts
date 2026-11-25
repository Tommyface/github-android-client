plugins {
    id("gy.android.lib")
    alias(libs.plugins.ksp)
}

dependencies {
    implementation(projects.core.mutation)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.bundles.koin.common)
    implementation(libs.koin.android)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
