/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P-101 验收用例（与 `spec/parity/P-101-update-semantic-level.md` 的用例表一一对应）。
 *
 * 这组用例是「更新判定」这一最容易被误伤用户的功能的唯一防线：
 * 判错方向要么无谓阻断用户，要么让用户错过安全更新。
 */
class UpdateLevelDeciderTest {
    @Suppress("LongParameterList")
    private fun decide(
        localVersion: String,
        remoteVersion: String,
        localFingerprint: String? = null,
        remoteFingerprint: String? = null,
        skipped: Set<SkipKey> = emptySet(),
        lastInstallRequestedAt: Long? = null,
        now: Long = 0L,
    ): UpdateDecision =
        UpdateLevelDecider.decide(
            UpdateDecisionInput(
                localVersion = localVersion,
                localFingerprint = localFingerprint,
                remoteVersion = remoteVersion,
                remoteFingerprint = remoteFingerprint,
                skippedKeys = skipped,
                lastInstallRequestedAt = lastInstallRequestedAt,
                now = now,
            ),
        )

    // ── 用例 1：主/次版本变化 → 强制更新 ──────────────────────────────
    @Test
    fun majorMinorChangeForcesUpdate() {
        val decision = decide(localVersion = "0.17.0", remoteVersion = "0.19.0")
        assertEquals(UpdateLevel.FORCED, decision.level)
    }

    // ── 用例 2：仅修订号变化 → 可选更新 ──────────────────────────────
    @Test
    fun patchOnlyChangeIsOptional() {
        val decision = decide(localVersion = "0.19.0", remoteVersion = "0.19.1")
        assertEquals(UpdateLevel.OPTIONAL, decision.level)
    }

    // ── 用例 3：同版本号但指纹不同 → 可选更新（不强制） ──────────────
    @Test
    fun sameVersionDifferentFingerprintIsOptionalNotForced() {
        val decision =
            decide(
                localVersion = "0.19.0",
                remoteVersion = "0.19.0",
                localFingerprint = "aaa",
                remoteFingerprint = "bbb",
            )
        assertEquals(UpdateLevel.OPTIONAL, decision.level)
    }

    // ── 用例 4：版本号不可解析 → 静默（绝不兜底 0.0.0） ──────────────
    @Test
    fun unparsableVersionIsSilent() {
        val decision = decide(localVersion = "0.19.0", remoteVersion = "abc")
        assertEquals(UpdateLevel.UNKNOWN, decision.level)
        assertTrue(decision.isSilent)
    }

    @Test
    fun blankRemoteVersionIsSilent() {
        val decision = decide(localVersion = "0.19.0", remoteVersion = "")
        assertEquals(UpdateLevel.UNKNOWN, decision.level)
    }

    // ── 用例 5：命中跳过键 → 静默 ────────────────────────────────────
    @Test
    fun skippedFingerprintIsSilent() {
        val decision =
            decide(
                localVersion = "0.19.0",
                remoteVersion = "0.19.1",
                remoteFingerprint = "abc",
                skipped = setOf(SkipKey("0.19.1", "abc")),
            )
        assertEquals(UpdateLevel.NONE, decision.level)
        assertTrue(decision.isSilent)
    }

    @Test
    fun skipWithDifferentFingerprintStillReminds() {
        val decision =
            decide(
                localVersion = "0.19.0",
                remoteVersion = "0.19.1",
                remoteFingerprint = "new",
                skipped = setOf(SkipKey("0.19.1", "old")),
            )
        assertEquals(UpdateLevel.OPTIONAL, decision.level)
    }

    // ── 用例 6：安装 TTL 内不重复提醒 ─────────────────────────────────
    @Test
    fun installTtlSuppressesReminder() {
        val now = 10_000L
        val decision =
            decide(
                localVersion = "0.19.0",
                remoteVersion = "0.19.1",
                lastInstallRequestedAt = now - 60_000L,
                now = now,
            )
        assertEquals(UpdateLevel.NONE, decision.level)
    }

    @Test
    fun afterTtlReminderResumes() {
        val now = 10_000L
        val decision =
            decide(
                localVersion = "0.19.0",
                remoteVersion = "0.19.1",
                lastInstallRequestedAt = now - 31 * 60 * 1000L,
                now = now,
            )
        assertEquals(UpdateLevel.OPTIONAL, decision.level)
    }

    // ── 用例 7：已是最新 → 完全静默 ──────────────────────────────────
    @Test
    fun alreadyLatestIsSilent() {
        val decision =
            decide(
                localVersion = "0.19.0",
                remoteVersion = "0.19.0",
                localFingerprint = "same",
                remoteFingerprint = "same",
            )
        assertEquals(UpdateLevel.NONE, decision.level)
        assertTrue(decision.isSilent)
    }

    @Test
    fun remoteOlderThanLocalIsSilent() {
        val decision = decide(localVersion = "0.19.1", remoteVersion = "0.19.0")
        assertEquals(UpdateLevel.NONE, decision.level)
    }

    // ── 边界：指纹缺失不误报 ─────────────────────────────────────────
    @Test
    fun missingFingerprintDoesNotFalsePositive() {
        val decision =
            decide(
                localVersion = "0.19.0",
                remoteVersion = "0.19.0",
                localFingerprint = null,
                remoteFingerprint = null,
            )
        assertEquals(UpdateLevel.NONE, decision.level)
    }

    @Test
    fun missingRemoteFingerprintFallsBackToVersionComparison() {
        val decision =
            decide(
                localVersion = "0.19.0",
                remoteVersion = "0.20.0",
                localFingerprint = "aaa",
                remoteFingerprint = null,
            )
        assertEquals(UpdateLevel.FORCED, decision.level)
    }

    // ── SemVer 解析 ──────────────────────────────────────────────────
    @Test
    fun semVerParsesExpectedShapes() {
        assertEquals(SemVer(1, 2, 3), SemVer.parse("1.2.3"))
        assertEquals(SemVer(1, 2, 3), SemVer.parse("v1.2.3"))
        assertEquals(SemVer(1, 2, 3), SemVer.parse("1.2.3-rc1"))
        assertNull(SemVer.parse("1.2"))
        assertNull(SemVer.parse("abc"))
        assertNull(SemVer.parse(null))
        assertNull(SemVer.parse(""))
    }

    @Test
    fun semVerComparesCorrectly() {
        assertTrue(SemVer.parse("0.19.1")!! > SemVer.parse("0.19.0")!!)
        assertTrue(SemVer.parse("0.20.0")!! > SemVer.parse("0.19.9")!!)
        assertTrue(SemVer.parse("1.0.0")!! > SemVer.parse("0.99.99")!!)
    }
}
