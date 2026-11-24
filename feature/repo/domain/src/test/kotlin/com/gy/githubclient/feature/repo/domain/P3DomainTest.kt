/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain

import com.gy.githubclient.feature.repo.domain.model.Milestone
import com.gy.githubclient.feature.repo.domain.model.milestoneProgressPercent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** P3 领域逻辑（v0.17.0）：里程碑完成度纯函数 + 派生字段。 */
class P3DomainTest {
    @Test
    fun progressPercentComputesClosedRatio() {
        assertEquals(50, milestoneProgressPercent(2, 2))
        assertEquals(25, milestoneProgressPercent(3, 1))
        assertEquals(100, milestoneProgressPercent(0, 5))
    }

    @Test
    fun progressPercentHandlesZeroAndNegative() {
        assertEquals(0, milestoneProgressPercent(0, 0))
        assertEquals(0, milestoneProgressPercent(-3, -1))
        assertEquals(100, milestoneProgressPercent(-1, 4))
    }

    @Test
    fun progressPercentFloorsFraction() {
        assertEquals(33, milestoneProgressPercent(2, 1))
        assertEquals(66, milestoneProgressPercent(3, 6))
    }

    @Test
    fun milestoneDerivedFields() {
        val open =
            Milestone(
                number = 3,
                title = "v1",
                state = "open",
                description = null,
                openIssues = 4,
                closedIssues = 6,
                htmlUrl = "https://github.com/o/r/milestone/3",
            )
        assertTrue(open.isOpen)
        assertEquals(10, open.totalIssues)
        assertEquals(60, milestoneProgressPercent(open.openIssues, open.closedIssues))

        val closed = open.copy(state = "closed")
        assertFalse(closed.isOpen)
    }
}
