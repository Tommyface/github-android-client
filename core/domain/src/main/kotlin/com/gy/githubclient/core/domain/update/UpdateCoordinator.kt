/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.update

/**
 * 一次更新检查的结论（领域层）。
 */
data class UpdateOutcome(
    /** 判定级别（NONE / OPTIONAL / FORCED / UNKNOWN）。 */
    val level: UpdateLevel,
    /** 远端候选（无更新或不可比较时为 null）。 */
    val candidate: UpdateCandidate? = null,
    /** 判定理由（面向诊断与日志，不直接展示原文）。 */
    val reason: String,
    /** 本地版本号。 */
    val currentVersion: String,
) {
    /** 是否需要打扰用户（NONE 与 UNKNOWN 一律静默）。 */
    val shouldNotify: Boolean
        get() = level == UpdateLevel.OPTIONAL || level == UpdateLevel.FORCED

    /** 是否强制（不可跳过）。 */
    val isForced: Boolean
        get() = level == UpdateLevel.FORCED
}

/**
 * 更新检查协调器（领域层，纯逻辑）。
 *
 * 职责：
 *  1. 从 [UpdateCandidateSource] 取回**已合并**的远端候选（双通道合并在 source 内完成）；
 *  2. 依据 [UpdateSkipLedger]（跳过指纹、安装 TTL）与本机版本/指纹，交给 [UpdateLevelDecider] 判定；
 *  3. 输出 [UpdateOutcome]。
 *
 * 之所以放在领域层：更新判定是"最容易误伤用户"的一环（判错要么无谓阻断、要么错过安全更新），
 * 必须能在没有 Android、没有网络的情况下被穷举测试。
 */
class UpdateCoordinator(
    private val source: UpdateCandidateSource,
    private val ledger: UpdateSkipLedger,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun evaluate(
        localVersion: String,
        localFingerprint: String? = null,
        localVersionCode: Int? = null,
    ): UpdateOutcome {
        val candidate = source.fetch()
        if (candidate == null) {
            return UpdateOutcome(
                level = UpdateLevel.UNKNOWN,
                reason = "远端无可用信息（两个通道均失败）",
                currentVersion = localVersion,
            )
        }
        val decision =
            UpdateLevelDecider.decide(
                UpdateDecisionInput(
                    localVersion = localVersion,
                    localFingerprint = localFingerprint,
                    remoteVersion = candidate.version,
                    localVersionCode = localVersionCode,
                    remoteVersionCode = candidate.versionCode,
                    remoteFingerprint = candidate.fingerprint,
                    skippedKeys = ledger.skippedKeys(),
                    lastInstallRequestedAt = ledger.lastInstallRequestedAt(),
                    now = now(),
                ),
            )
        return UpdateOutcome(
            level = decision.level,
            candidate = candidate.takeIf { decision.level != UpdateLevel.NONE },
            reason = decision.reason,
            currentVersion = localVersion,
        )
    }

    /** 记录一次跳过（用户点了"跳过此版"）。 */
    fun skip(candidate: UpdateCandidate) {
        ledger.recordSkip(SkipKey(candidate.version, candidate.fingerprint.orEmpty()))
    }

    /** 记录一次安装点击（触发安装 TTL，防装失败死循环）。 */
    fun markInstallRequested() {
        ledger.recordInstallRequested(now())
    }

    /** 是否处于检查节流窗口内。 */
    fun isThrottled(): Boolean =
        isWithinThrottle(
            lastCheckAt = ledger.lastCheckAt(),
            now = now(),
            throttleMillis = ledger.throttleMillis(),
        )

    /** 记录一次检查时间。 */
    fun recordCheck() {
        ledger.recordCheckAt(now())
    }
}
