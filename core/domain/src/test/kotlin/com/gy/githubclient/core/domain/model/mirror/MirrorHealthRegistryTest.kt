/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.model.mirror

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M1-05：镜像健康度与熔断。 */
class MirrorHealthRegistryTest {
    @Test
    fun successResetsConsecutiveFailures() {
        val registry = MirrorHealthRegistry()
        registry.recordFailure("a", now = 0L)
        registry.recordFailure("a", now = 0L)
        registry.recordSuccess("a")
        assertEquals(0, registry.healthOf("a").consecutiveFailures)
        assertEquals(2, registry.healthOf("a").failureCount)
        assertEquals(1, registry.healthOf("a").successCount)
    }

    @Test
    fun opensCircuitAfterThreshold() {
        val registry = MirrorHealthRegistry(failureThreshold = 3, cooldownMillis = 60_000L)
        repeat(2) { registry.recordFailure("a", now = 0L) }
        assertTrue(registry.openCircuitIds(0L).isEmpty())
        registry.recordFailure("a", now = 0L)
        assertEquals(setOf("a"), registry.openCircuitIds(0L))
    }

    @Test
    fun circuitHalfOpensAfterCooldown() {
        val registry = MirrorHealthRegistry(failureThreshold = 1, cooldownMillis = 60_000L)
        registry.recordFailure("a", now = 0L)
        assertTrue(registry.openCircuitIds(30_000L).contains("a"))
        assertFalse(registry.openCircuitIds(61_000L).contains("a"))
    }

    @Test
    fun successClosesCircuit() {
        val registry = MirrorHealthRegistry(failureThreshold = 1, cooldownMillis = 60_000L)
        registry.recordFailure("a", now = 0L)
        registry.recordSuccess("a")
        assertTrue(registry.openCircuitIds(1_000L).isEmpty())
    }

    @Test
    fun ranksByHealthScore() {
        val registry = MirrorHealthRegistry()
        repeat(5) { registry.recordFailure("bad", now = 0L) }
        repeat(5) { registry.recordSuccess("good") }
        val ranked = registry.rankByHealth(listOf("bad", "good"), latencies = mapOf("bad" to 100, "good" to 100))
        assertEquals("good", ranked.first())
    }

    @Test
    fun unknownMirrorHasNeutralScore() {
        val registry = MirrorHealthRegistry()
        assertEquals(
            MirrorHealth.DEFAULT_SUCCESS_RATE,
            registry.healthOf("unknown").successRate,
            0.0001,
        )
    }
}
