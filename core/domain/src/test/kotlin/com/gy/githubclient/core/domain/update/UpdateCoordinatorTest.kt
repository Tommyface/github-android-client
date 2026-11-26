/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M1-02/M1-03：双通道合并与协调器的纯逻辑验收。
 */
class UpdateCoordinatorTest {
    private class FakeSource(
        var candidate: UpdateCandidate?,
    ) : UpdateCandidateSource {
        override suspend fun fetch(): UpdateCandidate? = candidate
    }

    private class FakeLedger(
        override var skipped: MutableSet<SkipKey> = mutableSetOf(),
        override var lastInstall: Long? = null,
        override var lastCheck: Long = 0L,
        override var throttle: Long = 30 * 60 * 1000L,
    ) : UpdateSkipLedger {
        override fun skippedKeys(): Set<SkipKey> = skipped

        override fun recordSkip(key: SkipKey) {
            skipped.add(key)
        }

        override fun lastInstallRequestedAt(): Long? = lastInstall

        override fun recordInstallRequested(at: Long) {
            lastInstall = at
        }

        override fun lastCheckAt(): Long = lastCheck

        override fun recordCheckAt(at: Long) {
            lastCheck = at
        }

        override fun throttleMillis(): Long = throttle
    }

    @Test
    fun mergePrefersPrimaryChannel() {
        val primary = UpdateCandidate(version = "0.19.0", fingerprint = "aaa")
        val fallback = UpdateCandidate(version = "0.20.0", fingerprint = "bbb")
        val merged = UpdateChannelMerger.merge(primary, fallback)
        assertEquals("0.19.0", merged?.version)
        assertEquals("aaa", merged?.fingerprint)
    }

    @Test
    fun mergeFallsBackWhenPrimaryMissing() {
        val fallback = UpdateCandidate(version = "0.20.0", fingerprint = "bbb")
        assertEquals("0.20.0", UpdateChannelMerger.merge(null, fallback)?.version)
        assertNull(UpdateChannelMerger.merge(null, null))
    }

    @Test
    fun mergeBorrowsFingerprintFromFallback() {
        val primary = UpdateCandidate(version = "0.19.0", fingerprint = null)
        val fallback = UpdateCandidate(version = "0.19.0", fingerprint = "bbb")
        val merged = UpdateChannelMerger.merge(primary, fallback)
        assertEquals("0.19.0", merged?.version)
        assertEquals("bbb", merged?.fingerprint)
    }

    @Test
    fun coordinatorReportsOptionalWhenOnlyPatchChanged() {
        val source = FakeSource(UpdateCandidate(version = "0.19.1", fingerprint = "abc"))
        val coordinator = UpdateCoordinator(source, FakeLedger()) { 0L }
        val outcome = coordinator.evaluate(localVersion = "0.19.0", localVersionCode = 20)
        assertEquals(UpdateLevel.OPTIONAL, outcome.level)
        assertTrue(outcome.shouldNotify)
        assertFalse(outcome.isForced)
    }

    @Test
    fun coordinatorReportsForcedWhenVersionCodeJumps() {
        val source = FakeSource(UpdateCandidate(version = "0.20.0", versionCode = 30, fingerprint = "abc"))
        val coordinator = UpdateCoordinator(source, FakeLedger()) { 0L }
        val outcome = coordinator.evaluate(localVersion = "0.19.0", localVersionCode = 20)
        assertEquals(UpdateLevel.FORCED, outcome.level)
        assertTrue(outcome.isForced)
    }

    @Test
    fun coordinatorSilencesWhenAlreadyLatest() {
        val source = FakeSource(UpdateCandidate(version = "0.19.0", versionCode = 20, fingerprint = "abc"))
        val coordinator = UpdateCoordinator(source, FakeLedger()) { 0L }
        val outcome = coordinator.evaluate(localVersion = "0.19.0", localVersionCode = 20)
        assertEquals(UpdateLevel.NONE, outcome.level)
        assertFalse(outcome.shouldNotify)
    }

    @Test
    fun coordinatorSilencesSkippedFingerprint() {
        val source = FakeSource(UpdateCandidate(version = "0.19.1", fingerprint = "abc"))
        val ledger = FakeLedger(skipped = mutableSetOf(SkipKey("0.19.1", "abc")))
        val coordinator = UpdateCoordinator(source, ledger) { 0L }
        val outcome = coordinator.evaluate(localVersion = "0.19.0")
        assertEquals(UpdateLevel.NONE, outcome.level)
    }

    @Test
    fun skipRecordsFingerprintNotVersion() {
        val source = FakeSource(UpdateCandidate(version = "0.19.1", fingerprint = "abc"))
        val ledger = FakeLedger()
        val coordinator = UpdateCoordinator(source, ledger) { 0L }
        coordinator.skip(UpdateCandidate(version = "0.19.1", fingerprint = "abc"))
        assertTrue(ledger.skippedKeys().contains(SkipKey("0.19.1", "abc")))
        // 同版本号但指纹不同的新产物不受影响
        val outcome = coordinator.evaluate(localVersion = "0.19.0")
        assertEquals(UpdateLevel.OPTIONAL, outcome.level)
    }

    @Test
    fun installTtlSuppressesReminder() {
        val source = FakeSource(UpdateCandidate(version = "0.19.1", fingerprint = "abc"))
        val ledger = FakeLedger(lastInstall = 1_000L)
        val coordinator = UpdateCoordinator(source, ledger) { 1_060L }
        val outcome = coordinator.evaluate(localVersion = "0.19.0")
        assertEquals(UpdateLevel.NONE, outcome.level)
    }

    @Test
    fun throttleWindowIsRespected() {
        val ledger = FakeLedger(lastCheck = 1_000L, throttle = 60_000L)
        val coordinator = UpdateCoordinator(FakeSource(null), ledger) { 1_030L }
        assertTrue(coordinator.isThrottled())
        val later = UpdateCoordinator(FakeSource(null), ledger) { 61_100L }
        assertFalse(later.isThrottled())
    }

    @Test
    fun unknownWhenNoRemoteCandidate() {
        val coordinator = UpdateCoordinator(FakeSource(null), FakeLedger()) { 0L }
        val outcome = coordinator.evaluate(localVersion = "0.19.0")
        assertEquals(UpdateLevel.UNKNOWN, outcome.level)
        assertFalse(outcome.shouldNotify)
    }
}
