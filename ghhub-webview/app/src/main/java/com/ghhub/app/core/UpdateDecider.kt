package com.ghhub.app.core

/**
 * 纯决策器（不依赖 Android / 网络），便于单元测试。
 * 严格对应方案 6.2 的状态迁移规则。
 */
object UpdateDecider {

    data class RemoteInfo(
        val version: String,
        val fingerprint: String,
        val changelog: List<String> = emptyList()
    )

    data class LocalInfo(
        val version: String,
        val fingerprint: String,
        val isForceLevel: Boolean = false
    )

    /**
     * @param skippedKeys 该应用已跳过的双键集合
     * @param forceByMajorMinor 远端相对本地是否发生主/次版本变化（由调用方按语义版本计算）
     */
    fun decide(
        local: LocalInfo,
        remote: RemoteInfo,
        skippedKeys: Set<SkipKey>,
        forceByMajorMinor: Boolean
    ): UpdateState {
        val cur = SemVer.parse(local.version)
        val tgt = SemVer.parse(remote.version)

        // 规则一：解析失败 → Unknown，静默，绝不兜底 0.0.0
        if (cur == null || tgt == null) {
            return UpdateState.Unknown("版本号解析失败（${local.version} → ${remote.version}）→ 静默，绝不兜底 0.0.0")
        }

        val fpSame = local.fingerprint.equals(remote.fingerprint, ignoreCase = true)

        // 规则三：指纹不一致（即使版本号相同）或主/次版本变化 → 强制，忽略 skip
        if (!fpSame || forceByMajorMinor || local.isForceLevel) {
            val why = when {
                !fpSame -> "指纹不一致（同版本号构建变化）"
                else -> "主/次版本变化"
            }
            return UpdateState.ForcedUpdate(
                current = local.version, target = remote.version,
                fingerprint = remote.fingerprint, reason = "$why → 强制更新，忽略 skip"
            )
        }

        // 规则二：版本不新且指纹一致 → NoUpdate 静默
        if (tgt <= cur) {
            return UpdateState.NoUpdate
        }

        // 规则四：仅补丁变化 → 可选；命中 skip 双键 → 静默（#40 修复）
        if (SkipKey(remote.version, remote.fingerprint) in skippedKeys) {
            return UpdateState.NoUpdate
        }

        return UpdateState.OptionalUpdate(
            current = local.version, target = remote.version,
            fingerprint = remote.fingerprint, changelog = remote.changelog
        )
    }

    /** 主/次版本是否变化（补丁变化不算强制）。 */
    fun isMajorOrMinorBump(local: String, remote: String): Boolean {
        val a = SemVer.parse(local) ?: return false
        val b = SemVer.parse(remote) ?: return false
        return b.major > a.major || b.minor > a.minor
    }
}
