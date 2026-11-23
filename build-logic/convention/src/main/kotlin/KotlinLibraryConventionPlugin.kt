import org.gradle.api.Plugin
import org.gradle.api.Project

/** 纯 Kotlin 模块（domain 层），无 Android 依赖，可 JVM 单测。 */
class KotlinLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.jvm")
            pluginManager.apply("gy.quality")

            // JVM toolchain 与依赖配置
            extensions.configure(org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension::class.java) {
                jvmToolchain(20)
            }
            dependencies.add("testImplementation", "junit:junit:4.13.2")
        }
    }
}
