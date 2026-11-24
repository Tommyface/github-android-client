/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain

import com.gy.githubclient.feature.repo.domain.model.CheckConclusion
import com.gy.githubclient.feature.repo.domain.model.CheckOverview
import com.gy.githubclient.feature.repo.domain.model.CheckRun
import com.gy.githubclient.feature.repo.domain.model.CommitStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.13.0 A2 回归：CI 结论归一化与总览聚合。 */
class CheckDomainTest {
    @Test
    fun conclusionNormalizesRawValues() {
        assertEquals(CheckConclusion.SUCCESS, CheckConclusion.from("success"))
        assertEquals(CheckConclusion.FAILURE, CheckConclusion.from("timed_out"))
        assertEquals(CheckConclusion.FAILURE, CheckConclusion.from("ERROR"))
        assertEquals(CheckConclusion.NEUTRAL, CheckConclusion.from("skipped"))
        assertEquals(CheckConclusion.PENDING, CheckConclusion.from("in_progress"))
        assertEquals(CheckConclusion.UNKNOWN, CheckConclusion.from(null))
    }

    @Test
    fun checkOverviewAggregatesOverall() {
        val success =
            CheckOverview(
                sha = "abc",
                runs = listOf(CheckRun("build", "completed", "success", null)),
                statuses = listOf(CommitStatus("lint", "success", null)),
            )
        assertEquals(CheckConclusion.SUCCESS, success.overall)
        assertTrue(!success.isEmpty)

        val withFailure =
            success.copy(
                statuses = success.statuses + CommitStatus("test", "failure", null),
            )
        assertEquals(CheckConclusion.FAILURE, withFailure.overall)

        val pending =
            CheckOverview(
                sha = "abc",
                runs = listOf(CheckRun("build", "in_progress", null, null)),
                statuses = emptyList(),
            )
        assertEquals(CheckConclusion.PENDING, pending.overall)
    }

    @Test
    fun emptyOverviewIsUnknown() {
        val empty = CheckOverview(sha = "abc")
        assertTrue(empty.isEmpty)
        assertEquals(CheckConclusion.UNKNOWN, empty.overall)
    }

    @Test
    fun runNormalizedFallsBackToStatusWhenConclusionNull() {
        val run = CheckRun("build", "in_progress", null, null)
        assertEquals(CheckConclusion.PENDING, run.normalized)
    }
}
