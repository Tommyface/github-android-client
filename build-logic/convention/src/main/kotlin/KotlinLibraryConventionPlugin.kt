import org.gradle.api.Plugin
import org.gradle.api.Project

/** 纯 Kotlin 模块（domain 层），无 Android 依赖，可 JVM 单测。 */
class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("gy.quality")

            // JVM toolchain 与依赖配置。
            // 与 Android 模块的 JVM target 统一为 17（M0-12）：
            // 此前设为 20 会让仅安装 JDK 17 的环境无法构建 domain 层，是可移植性隐患。
            extensions.configure(org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension::class.java) {
                jvmToolchain(17)
            }
            dependencies.add("testImplementation", "junit:junit:4.13.2")
        }
    }
}
