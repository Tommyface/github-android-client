package com.ghhub.app.core

/**
 * 更新状态机（方案 6.2，核心差异化，杜绝 githup #40）。
 *
 * 硬约束：
 *  - NoUpdate 完全静默；
 *  - Unknown 绝不兜底 0.0.0（版本号解析失败就静默）；
 *  - ForcedUpdate 忽略 skip，指纹不一致即使版本号相同也触发；
 *  - OptionalUpdate 的 skip 按「版本号 + 指纹」双键记录。
 */
sealed class UpdateState(open val reason: String = "") {
    data object Idle : UpdateState("启动 / 切前台超 30min 触发检查")
    data object Checking : UpdateState("双通道并行：Release API + version.json，谁先成功用谁")
    data object NoUpdate : UpdateState("版本不新且指纹一致 → 完全静默")
    data class OptionalUpdate(
        val current: String,
        val target: String,
        val fingerprint: String,
        val changelog: List<String> = emptyList(),
        override val reason: String = "仅补丁版更新 → 提醒一次，可 skip"
    ) : UpdateState(reason)

    data class ForcedUpdate(
        val current: String,
        val target: String,
        val fingerprint: String,
        override val reason: String = "主/次版本变化或指纹不一致 → 强提醒，忽略 skip"
    ) : UpdateState(reason)

    data class Unknown(override val reason: String = "版本号解析失败 → 静默，绝不兜底 0.0.0") : UpdateState(reason)
    data class Downloading(val target: String, val mirrorHost: String) : UpdateState("用户确认 → AppUpdater 下载 + MD5/sha256 校验")
    data class ReadyToInstall(val apkPath: String) : UpdateState("校验通过 → FileProvider 授权系统安装器")

    val name: String get() = this::class.simpleName ?: "Unknown"
}

/** 语义化版本（借鉴 PyPI github-release-downloader：兼容范围筛选，而非只比大小）。 */
data class SemVer(val major: Int, val minor: Int, val patch: Int, val raw: String) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    companion object {
        private val RE = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$""")
        /** 解析失败返回 null —— 调用方必须走 Unknown，禁止兜底 0.0.0。 */
        fun parse(value: String?): SemVer? {
            if (value.isNullOrBlank()) return null
            val m = RE.matchEntire(value.trim()) ?: return null
            return SemVer(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt(), value)
        }
    }
}

/** skip 双键：版本号 + 指纹。缺一不可。 */
data class SkipKey(val version: String, val fingerprint: String)
