import org.gradle.api.Project

/**
 * 版本单一真源读取器（M0-01）。
 *
 * 版本号只在 `gradle.properties` 定义一次：
 * ```
 * ghcVersionName=0.19.0
 * ghcVersionCode=21
 * ```
 *
 * 任何模块都不得硬编码版本号。CI 的「三处一致校验」以本文件读到的值为准：
 * `gradle.properties` == git tag == `version.json`。
 *
 * 读取优先级：Gradle 属性 -> 环境变量（CI 可覆盖）-> 失败即抛错。
 * 刻意不提供"兜底默认值"：静默兜底会让版本语义在无人察觉时漂移，
 * 这正是历史事故「版本号没变、值却是上一个包」的成因。
 */
internal object VersionSource {
    const val PROPERTY_NAME = "ghcVersionName"
    const val PROPERTY_CODE = "ghcVersionCode"

    fun versionName(project: Project): String =
        resolve(project, PROPERTY_NAME, "GHC_VERSION_NAME")

    fun versionCode(project: Project): Int {
        val raw = resolve(project, PROPERTY_CODE, "GHC_VERSION_CODE")
        return raw.toIntOrNull()
            ?: error(
                "$PROPERTY_CODE 必须是整数，当前值：'$raw'。" +
                    "请修正 gradle.properties，禁止使用非数字版本号。",
            )
    }

    private fun resolve(project: Project, property: String, env: String): String =
        project.providers.gradleProperty(property).orNull
            ?: project.providers.environmentVariable(env).orNull
            ?: error(
                "缺少版本真源 $property。" +
                    "请在 gradle.properties 中声明 $property（版本号唯一真源）。",
            )
}
