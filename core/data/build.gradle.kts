plugins {
    id("gy.android.lib")
    alias(libs.plugins.ksp)
    // 写操作的错误信封（GitHubErrorEnvelope）需要 @Serializable 生成 serializer
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.mutation)
    implementation(projects.core.permission)
    implementation(libs.bundles.ktor.common)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.bundles.koin.common)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.datetime)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.kotlinx.coroutines.test)
}
