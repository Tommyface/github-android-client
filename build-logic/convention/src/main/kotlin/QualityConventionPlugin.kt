import io.gitlab.arturbosch.detekt.DetektPlugin
import io.gitlab.arturbosch.detekt.extensions.DetektExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType

/**
 * 8 项质量门禁的集中配置（门禁 2/3/5）：
 * - detekt 静态分析
 * - spotless (ktlint) 格式检查
 * - kover 覆盖率
 * 门禁 1/4/6/7/8 分别由 assemble / test / konsist / lint / migration test 承担。
 */
class QualityConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            // 门禁 2：detekt
            pluginManager.apply(DetektPlugin::class.java)
            extensions.configure<DetektExtension> {
                config.setFrom(rootProject.file("config/detekt/detekt.yml"))
                buildUponDefaultConfig = true
                parallel = true
            }
            tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
                jvmTarget = "17"
                reports {
                    html.required.set(true)
                    xml.required.set(true)
                }
            }
            // 门禁 3：spotless + ktlint
            pluginManager.apply("com.diffplug.spotless")
            val spotlessExt = extensions.getByName("spotless") as com.diffplug.gradle.spotless.SpotlessExtension
            spotlessExt.apply {
                kotlin {
                    target("**/*.kt")
                    targetExclude("**/build/**", "**/generated/**")
                    // Compose 可组合函数按惯例大写开头，禁用 standard:function-naming
                    ktlint("1.3.0").setEditorConfigPath(rootProject.file("config/spotless/.editorconfig"))
                    licenseHeaderFile(rootProject.file("config/spotless/license.header"))
                }
                kotlinGradle {
                    target("*.gradle.kts")
                    ktlint("1.3.0")
                }
            }

            // 门禁 5：kover 覆盖率
            pluginManager.apply("org.jetbrains.kotlinx.kover")
        }
    }
}
