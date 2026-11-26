package com.ghhub.app.mirror

import android.content.Context

/** 单个镜像源配置（复用现有 MirrorConfig 设计）。 */
data class MirrorSource(
    val id: String,
    val host: String,
    val template: String,          // 形如 https://ghproxy.com/{url}
    val custom: Boolean = false,
    var latencyMs: Long? = null,
    var lossPercent: Int = 0,
    var successCount: Int = 0,
    var failCount: Int = 0
)

/** 镜像配置仓库：内置 3 源 + 直连 + 自定义源（issue #35），成败记录持久化用于下次选源权重。 */
class MirrorConfig(context: Context) {
    private val prefs = context.getSharedPreferences("ghhub_mirrors", Context.MODE_PRIVATE)

    val sources: MutableList<MirrorSource> = buildList {
        add(MirrorSource("ghproxy", "ghproxy.com", "https://ghproxy.com/{url}"))
        add(MirrorSource("gitclone", "gitclone.com", "https://gitclone.com/github.com/{url}"))
        add(MirrorSource("moeyy", "moeyy.cn", "https://moeyy.cn/github.com/{url}"))
        add(MirrorSource("direct", "github.com（直连）", "{url}"))
        // 恢复自定义源
        val customNames = prefs.getStringSet(KEY_CUSTOM, emptySet()) ?: emptySet()
        customNames.forEach { name ->
            add(MirrorSource("custom_$name", name, "https://$name/{url}", custom = true))
        }
    }.toMutableList()

    fun addCustom(host: String): MirrorSource {
        val clean = host.trim().removePrefix("https://").removePrefix("http://").trimEnd('/')
        val src = MirrorSource("custom_$clean", clean, "https://$clean/{url}", custom = true)
        if (sources.none { it.host == clean }) {
            sources.add(src)
            prefs.edit().putStringSet(KEY_CUSTOM, (prefs.getStringSet(KEY_CUSTOM, emptySet()) ?: emptySet()) + clean).apply()
        }
        return src
    }

    fun resolve(src: MirrorSource, rawUrl: String): String {
        val stripped = rawUrl.removePrefix("https://").removePrefix("http://")
        return src.template.replace("{url}", stripped)
    }

    /** 记录成败，作为下次选源权重（方案 6.3）。 */
    fun recordResult(src: MirrorSource, success: Boolean) {
        if (success) src.successCount++ else src.failCount++
        prefs.edit()
            .putInt("${src.id}_ok", src.successCount)
            .putInt("${src.id}_fail", src.failCount)
            .apply()
    }

    companion object {
        private const val KEY_CUSTOM = "custom_hosts"
    }
}
