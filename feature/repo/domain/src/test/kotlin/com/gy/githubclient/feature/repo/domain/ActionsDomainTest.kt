/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain

import com.gy.githubclient.feature.repo.domain.model.RunStatus
import com.gy.githubclient.feature.repo.domain.model.WorkflowRun
import org.junit.Assert.assertEquals
import org.junit.Test

/** v0.14.0 A3 回归：run 状态归一化与派生字段。 */
class ActionsDomainTest {
    @Test
    fun runStatusNormalizesRawValues() {
        assertEquals(RunStatus.COMPLETED, RunStatus.from("completed"))
        assertEquals(RunStatus.IN_PROGRESS, RunStatus.from("in_progress"))
        assertEquals(RunStatus.QUEUED, RunStatus.from("queued"))
        assertEquals(RunStatus.QUEUED, RunStatus.from("waiting"))
        assertEquals(RunStatus.UNKNOWN, RunStatus.from(null))
    }

    @Test
    fun workflowRunDerivedFields() {
        val run =
            WorkflowRun(
                id = 1L,
                name = "CI",
                event = "push",
                status = "completed",
                conclusion = "success",
                headBranch = "main",
                headSha = "abcdef1234567890",
                runNumber = 5,
                htmlUrl = "u",
                createdAt = null,
                updatedAt = null,
            )
        assertEquals("abcdef1", run.shortSha)
        assertEquals(RunStatus.COMPLETED, run.normalizedStatus)
    }
}
