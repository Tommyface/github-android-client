/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.update

/**
 * P-101 · 更新语义判定（见 `spec/parity/P-101-update-semantic-level.md`）。
 *
 * 纯 Kotlin、零 Android 依赖、零网络依赖——这是本文件存在的全部理由：
 * 更新判定的错误会让用户要么被无谓阻断，要么错过安全更新，必须可被穷举测试。
 *
 * 七条行为（P-101 规格）：
 *  1. x.y 变化 → 强制更新（不提供跳过）
 *  2. 仅 z 变化 → 可选更新（可跳过）
 *  3. 版本号相同但指纹不同 → 可选更新（同版本重打包，可重新下载）
 *  4. 版本号无法解析 → 无法比较，完全静默，**绝不兜底 0.0.0**
 *  5. 跳过按**指纹**记录而非版本号
 *  6. 安装后 30 分钟 TTL 内不重复提醒
 *  7. 已是最新 → 完全静默
 */
enum class UpdateLevel {
    /** 无法比较（版本号不可解析或关键信息缺失）：完全静默。 */
    UNKNOWN,

    /** 已是最新：完全静默。 */
    NONE,

    /** 可选更新：可提示、可跳过。 */
    OPTIONAL,

    /** 强制更新：主/次版本变化，必须提示且不提供跳过。 */
    FORCED,
}

/**
 * 语义化版本。解析失败返回 null——**调用方必须走 [UpdateLevel.UNKNOWN]**，
 * 禁止任何形式的兜底默认值（历史事故：兜底 0.0.0 会让"已是最新"误判为"必须升级"）。
 */
data class SemVer(
    val major: Int,
    val minor: Int,
    val patch: Int,
) : Comparable<SemVer> {
    override fun compareTo(other: SemVer): Int =
        compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    companion object {
        private val PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)(?:[-+].*)?$""")

        fun parse(value: String?): SemVer? {
            if (value.isNullOrBlank()) return null
            val match = PATTERN.matchEntire(value.trim()) ?: return null
            return SemVer(
                major = match.groupValues[1].toIntOrNull() ?: return null,
                minor = match.groupValues[2].toIntOrNull() ?: return null,
                patch = match.groupValues[3].toIntOrNull() ?: return null,
            )
        }
    }
}

/**
 * 「跳过此版」的记录键：**版本号 + 指纹**双键，缺一不可。
 *
 * 只记版本号会在"同版本号重打包"时误伤（用户跳过了旧包，却被新包反复提醒）；
 * 只记指纹则无法解释用户意图。两者合用才能既准确又可解释。
 */
data class SkipKey(
    val version: String,
    val fingerprint: String,
)

/**
 * 更新判定的输入。刻意做成数据类而非散乱参数，让状态机只有一种组装方式。
 */
data class UpdateDecisionInput(
    /** 本地已安装版本号，形如 0.19.0。 */
    val localVersion: String,
    /** 本地已安装包的 SHA-256 摘要（可为空，表示未知）。 */
    val localFingerprint: String?,
    /** 远端版本号。 */
    val remoteVersion: String,
    /** 远端产物 SHA-256 摘要（可为空，表示未知）。 */
    val remoteFingerprint: String?,
    /** 用户已跳过的键集合（版本号 + 指纹双键）。 */
    val skippedKeys: Set<SkipKey> = emptySet(),
    /** 上次点击安装的时间戳；非空且在 TTL 内时不重复提醒。 */
    val lastInstallRequestedAt: Long? = null,
    /** 当前时间戳（便于测试注入）。 */
    val now: Long = 0L,
    /** 安装提醒 TTL：默认 30 分钟。 */
    val installTtlMillis: Long = UpdateLevelDecider.DEFAULT_INSTALL_TTL_MILLIS,
)

/**
 * 更新判定的输出。
 */
data class UpdateDecision(
    val level: UpdateLevel,
    val currentVersion: String? = null,
    val targetVersion: String? = null,
    val targetFingerprint: String? = null,
    /** 面向用户的判定理由（用于诊断与日志，不直接展示原文）。 */
    val reason: String,
) {
    val isSilent: Boolean
        get() = level == UpdateLevel.NONE || level == UpdateLevel.UNKNOWN
}

/**
 * 纯判定器。全部逻辑集中于此，便于穷举测试（对应门禁：核心逻辑必须有分支覆盖）。
 */
object UpdateLevelDecider {
    const val DEFAULT_INSTALL_TTL_MILLIS: Long = 30 * 60 * 1000L

    /**
     * 判定更新级别。
     *
     * 判定顺序不可调换：
     *  1. 先看"能否比较"——不可解析时任何比较结论都不可信，直接静默；
     *  2. 再看 TTL——安装刚失败过就别再骚扰用户；
     *  3. 再看是否已是最新；
     *  4. 再看是否被跳过；
     *  5. 最后才区分强制与可选。
     */
    fun decide(input: UpdateDecisionInput): UpdateDecision {
        val local = SemVer.parse(input.localVersion)
        val remote = SemVer.parse(input.remoteVersion)

        // 规则 4：无法解析 → 静默，绝不兜底 0.0.0。
        if (local == null || remote == null) {
            return UpdateDecision(
                level = UpdateLevel.UNKNOWN,
                reason = "版本号解析失败（${input.localVersion} → ${input.remoteVersion}），静默处理",
            )
        }

        // 规则 6：安装 TTL 内不重复提醒（防装失败死循环）。
        if (isWithinInstallTtl(input)) {
            return UpdateDecision(
                level = UpdateLevel.NONE,
                currentVersion = input.localVersion,
                targetVersion = input.remoteVersion,
                reason = "安装提醒 TTL 内，暂不重复提醒",
            )
        }

        val fingerprintChanged = fingerprintDiffers(input)
        val newer = remote > local
        val majorOrMinorChanged = remote.major != local.major || remote.minor != local.minor

        // 规则 7：已是最新 → 完全静默。
        if (!newer && !fingerprintChanged) {
            return UpdateDecision(
                level = UpdateLevel.NONE,
                currentVersion = input.localVersion,
                targetVersion = input.remoteVersion,
                reason = "已是最新",
            )
        }

        // 规则 5：命中跳过键（版本号 + 指纹双键）→ 静默。
        val skipKey = SkipKey(input.remoteVersion, input.remoteFingerprint.orEmpty())
        if (skipKey in input.skippedKeys) {
            return UpdateDecision(
                level = UpdateLevel.NONE,
                currentVersion = input.localVersion,
                targetVersion = input.remoteVersion,
                reason = "已跳过该指纹，不再提醒",
            )
        }

        // 规则 1：主/次版本变化 → 强制更新（忽略跳过）。
        if (majorOrMinorChanged) {
            return UpdateDecision(
                level = UpdateLevel.FORCED,
                currentVersion = input.localVersion,
                targetVersion = input.remoteVersion,
                targetFingerprint = input.remoteFingerprint,
                reason = "主/次版本变化（${input.localVersion} → ${input.remoteVersion}），强制更新",
            )
        }

        // 规则 2/3：仅修订号变化，或同版本号但指纹不同 → 可选更新。
        return UpdateDecision(
            level = UpdateLevel.OPTIONAL,
            currentVersion = input.localVersion,
            targetVersion = input.remoteVersion,
            targetFingerprint = input.remoteFingerprint,
            reason =
                if (fingerprintChanged) {
                    "同版本号但产物指纹不同，可重新下载"
                } else {
                    "仅修订号更新，可跳过"
                },
        )
    }

    /**
     * 指纹是否不同。两侧都为空视为"未知"，不判定为不同——否则每次检查都会误报"可更新"。
     */
    private fun fingerprintDiffers(input: UpdateDecisionInput): Boolean {
        val local = input.localFingerprint?.takeIf { it.isNotBlank() } ?: return false
        val remote = input.remoteFingerprint?.takeIf { it.isNotBlank() } ?: return false
        return !local.equals(remote, ignoreCase = true)
    }

    private fun isWithinInstallTtl(input: UpdateDecisionInput): Boolean {
        val last = input.lastInstallRequestedAt ?: return false
        if (input.now < last) return false
        return input.now - last < input.installTtlMillis
    }
}
