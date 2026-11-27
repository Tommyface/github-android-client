import com.android.build.api.dsl.ApplicationExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.application")
            pluginManager.apply("org.jetbrains.kotlin.android")
            pluginManager.apply("org.jetbrains.kotlin.plugin.serialization")
            pluginManager.apply("gy.compose")
            pluginManager.apply("gy.quality")

            extensions.configure(org.jetbrains.kotlin.gradle.dsl.KotlinAndroidProjectExtension::class.java) {
                compilerOptions {
                    jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
                }
            }

            extensions.configure<ApplicationExtension> {
                namespace = Config.namespacePrefix
                compileSdk = Config.compileSdk
                defaultConfig {
                    applicationId = Config.namespacePrefix
                    minSdk = Config.minSdk
                    targetSdk = Config.targetSdk
                    versionCode = VersionSource.versionCode(target)
                    versionName = VersionSource.versionName(target)
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                    buildConfigField(
                        "String",
                        "VERSION_NAME",
                        "\"${VersionSource.versionName(target)}\"",
                    )
                }
                // 固定 debug 签名：使用入库的 debug.keystore，保证跨机器/跨 CI 构建签名指纹一致，
                // 从而使新 APK 能覆盖安装已装版本（覆盖安装要求 applicationId 与签名证书均一致）。
                signingConfigs {
                    getByName("debug") {
                        storeFile = rootProject.file("app/debug.keystore")
                        storePassword = "android"
                        keyAlias = "androiddebugkey"
                        keyPassword = "android"
                    }
                    // release 签名：密钥文件由 CI 从 Secrets 还原到该路径；本地不存在时保持未签名，
                    // 由 release.yml 的「制品校验」门禁断言签名缺失即失败（禁止明文口令兜底）。
                    create("release") {
                        val keystore = rootProject.file("app/release.keystore")
                        if (keystore.exists()) {
                            storeFile = keystore
                            // 口令与别名来自 Gradle 属性或环境变量（release.yml 用环境变量注入，
                            // 避免把口令写进命令行或被跟踪的文件）。
                            storePassword =
                                providers.gradleProperty("ghcKeystorePassword")
                                    .orElse(providers.environmentVariable("GHC_KEYSTORE_PASSWORD"))
                                    .orNull
                            keyAlias =
                                providers.gradleProperty("ghcKeyAlias")
                                    .orElse(providers.environmentVariable("GHC_KEY_ALIAS"))
                                    .orNull
                            keyPassword =
                                providers.gradleProperty("ghcKeyPassword")
                                    .orElse(providers.environmentVariable("GHC_KEY_PASSWORD"))
                                    .orNull
                        }
                    }
                }
                buildTypes {
                    getByName("debug") {
                        signingConfig = signingConfigs.getByName("debug")
                    }
                    getByName("release") {
                        // 正式产物必须混淆 + 压缩资源（M0-07）：逆向门槛与包体同时收益。
                        isMinifyEnabled = true
                        isShrinkResources = true
                        proguardFiles(
                            getDefaultProguardFile("proguard-android-optimize.txt"),
                            "proguard-rules.pro",
                        )
                        signingConfig = signingConfigs.getByName("release")
                    }
                }
                compileOptions {
                    sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
                    targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
                }
            }
        }
    }
}
