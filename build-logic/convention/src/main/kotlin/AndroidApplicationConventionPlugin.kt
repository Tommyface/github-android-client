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
                    versionCode = 8
                    versionName = "0.5.3"
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
                }
                buildTypes {
                    getByName("debug") {
                        signingConfig = signingConfigs.getByName("debug")
                    }
                    getByName("release") {
                        isMinifyEnabled = false
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
