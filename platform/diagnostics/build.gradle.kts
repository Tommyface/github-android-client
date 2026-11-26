plugins {
    id("gy.android.lib")
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.core.ktx)

    testImplementation(libs.junit)
}
