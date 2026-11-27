plugins {
    id("gy.android.lib")
    alias(libs.plugins.ksp)
    // 写操作的错误信封（GitHubErrorEnvelope）需要 @Serializable 生成 serializer
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(projects.core.domain)
    // GitHubApiClient 的公开签名包含 AuditSink / MutationRetryPolicy，
    // 用 api 暴露，避免消费方（如 feature:*:data）出现 "Cannot access class"。
    api(projects.core.mutation)
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
