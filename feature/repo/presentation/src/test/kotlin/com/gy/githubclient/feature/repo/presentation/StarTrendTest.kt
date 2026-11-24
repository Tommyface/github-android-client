/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import com.gy.githubclient.feature.repo.domain.model.StarHistoryPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.7.0 M10 回归：星标趋势折线投影（纯函数，不依赖 Compose/Android）。 */
class StarTrendTest {
    @Test
    fun emptyAndSinglePoint() {
        assertTrue(projectTrend(emptyList(), 100f).isEmpty())
        val single = projectTrend(listOf(point(1, 10)), 100f, 1000f)
        assertEquals(1, single.size)
        assertEquals(500f, single.first().first, 0.001f) // 单点居中
    }

    @Test
    fun monotonicProjectionSpansFullWidthAndHeight() {
        val points = listOf(point(1, 0), point(2, 5), point(3, 10))
        val projected = projectTrend(points, canvasHeightPx = 100f, canvasWidthPx = 1000f)
        assertEquals(listOf(0f, 500f, 1000f), projected.map { it.first })
        // 最小值落底(100)，最大值落顶(0)，中间居中(50)。
        assertEquals(listOf(100f, 50f, 0f), projected.map { it.second })
    }

    @Test
    fun allZeroCurveSitsAtBaselineWithoutDivideByZero() {
        val points = listOf(point(1, 0), point(2, 0))
        val projected = projectTrend(points, 100f, 1000f)
        assertTrue(projected.all { it.second == 100f })
    }

    private fun point(
        week: Long,
        cumulative: Int,
    ) = StarHistoryPoint(weekStartEpoch = week, weeklyStars = cumulative, cumulativeStars = cumulative)
}
