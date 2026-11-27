plugins {
    id("gy.kotlin.lib")
}

dependencies {
    implementation(projects.core.domain)
    // RepoRepository 的公开签名含 MutationResult，需 api 暴露给消费方
    api(projects.core.mutation)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)
    testImplementation(libs.kotlinx.coroutines.test)
}
