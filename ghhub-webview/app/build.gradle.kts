import java.util.Base64

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// 覆盖安装（cover install）核心：签名固定 + versionCode 单调递增。
// CI 通过环境变量注入 GHUB_VERSION_CODE（GITHUB_RUN_NUMBER）；本地缺省 1。
// 签名密钥：优先读环境变量 GHUB_KEYSTORE_B64（CI Secrets），其次本地 ghhub-release.jks（base64 文本）；
// 两者都没有时退回 debug 默认签名，仍能编译，但本地包与 CI 包签名不同、不可互相覆盖。
val ciVersionCode: Int = (System.getenv("GHUB_VERSION_CODE") ?: "1").toIntOrNull() ?: 1
val ciVersionName: String = System.getenv("GHUB_VERSION_NAME") ?: "0.1.0"

// CI 注入 base64 密钥；本地从工程内 ghhub-release.jks.b64 读取（示例密钥，生产务必替换并改用 Secrets）
val keystoreB64: String? = System.getenv("GHUB_KEYSTORE_B64")
    ?: file("ghhub-release.jks.b64").takeIf { it.exists() }?.readText()?.trim()
val hasReleaseKeystore = !keystoreB64.isNullOrBlank()

// 配置期解码为实际 jks 文件，供 signingConfig 在配置期引用（本地与 CI 一致）
val decodedKeystore = file("ghhub-release.local.jks")
if (hasReleaseKeystore && !decodedKeystore.exists()) {
    decodedKeystore.writeBytes(Base64.getDecoder().decode(keystoreB64!!))
}

android {
    namespace = "com.ghhub.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.ghhub.app"
        minSdk = 24
        targetSdk = 34
        versionCode = ciVersionCode
        versionName = ciVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = decodedKeystore
                storePassword = System.getenv("GHUB_STORE_PASSWORD") ?: "ghhub2026"
                keyAlias = System.getenv("GHUB_KEY_ALIAS") ?: "ghhub"
                keyPassword = System.getenv("GHUB_KEY_PASSWORD") ?: "ghhub2026"
            }
        }
    }

    buildTypes {
        debug {
            // 本地 debug 也用同一示例密钥，保证 debug↔release 可覆盖安装
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasReleaseKeystore) signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")

    // 复用组件：AppUpdater 2.2.0（下载/进度/取消/MD5/安装）
    implementation("com.github.jenly1314.AppUpdater:app-updater:2.2.0")

    // 网络 + 加密（EncryptedSharedPreferences / AES256）
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // 协程 + lifecycle
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.1")

    // gson 用于结构化日志 / version.json 解析
    implementation("com.google.code.gson:gson:2.11.0")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
}
