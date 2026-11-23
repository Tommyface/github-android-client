import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Compose 支持（compose compiler + BOM + 常用 UI 依赖）。 */
class ComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlin.plugin.compose")
            dependencies {
                "implementation"(platform("androidx.compose:compose-bom:2024.12.01"))
                "implementation"("androidx.activity:activity-compose:1.9.3")
                "implementation"("androidx.navigation:navigation-compose:2.8.5")
                "implementation"("io.coil-kt:coil-compose:2.6.0")
                "debugImplementation"("androidx.compose.ui:ui-tooling:1.7.5")
            }
        }
    }
}
