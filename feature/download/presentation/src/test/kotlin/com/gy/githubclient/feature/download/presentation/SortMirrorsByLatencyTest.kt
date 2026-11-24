/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.presentation

import com.gy.githubclient.core.domain.model.mirror.MirrorConfig
import com.gy.githubclient.core.domain.model.mirror.MirrorStatus
import com.gy.githubclient.core.domain.model.mirror.MirrorType
import org.junit.Assert.assertEquals
import org.junit.Test

/** v0.6.0 M9 回归：测速延迟升序、未测速者保持原序排在末尾。 */
class SortMirrorsByLatencyTest {
    private fun mirror(id: String): MirrorConfig =
        MirrorConfig(
            id = id,
            name = id,
            urlTemplate = null,
            type = MirrorType.COMMUNITY,
            status = MirrorStatus.OK,
            latencyMs = null,
            lastCheckedAt = null,
        )

    @Test
    fun sortsByLatencyThenKeepsUnmeasuredOrder() {
        val mirrors = listOf(mirror("a"), mirror("b"), mirror("c"), mirror("d"))
        val latencies = mapOf("b" to 300, "c" to 80)
        val sorted = sortMirrorsByLatency(mirrors, latencies)
        assertEquals(listOf("c", "b", "a", "d"), sorted.map { it.id })
    }

    @Test
    fun emptyLatenciesKeepsOriginalOrder() {
        val mirrors = listOf(mirror("x"), mirror("y"))
        assertEquals(mirrors, sortMirrorsByLatency(mirrors, emptyMap()))
    }
}
