/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.search.presentation

import com.gy.githubclient.core.domain.network.ApiError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 搜索 / 收藏错误文案：403 语义区分（权限不足 ≠ 搜索失败），避免误导用户。 */
class SearchErrorMessageTest {
    @Test
    fun star403MentionsPermissionInsteadOfSearch() {
        val message = starMessage(ApiError.Http(403, "Forbidden"))

        assertTrue(message.startsWith("收藏失败（403）"))
        assertTrue(message.contains("星标写权限"))
        assertTrue(message.contains("Starring: Read and write"))
        assertTrue(message.contains("权限体检"))
        // 不再出现误导性的「搜索失败」字样。
        assertTrue(!message.contains("搜索失败"))
    }

    @Test
    fun search403ExplainsRateProtection() {
        val message = searchErrorMessage(ApiError.Http(403, "Forbidden"))

        assertTrue(message.contains("403"))
        assertTrue(message.contains("频次保护"))
    }

    @Test
    fun otherCodesKeepGenericWording() {
        assertEquals("收藏失败（422）", starMessage(ApiError.Http(422, "Unprocessable")))
        assertEquals("搜索失败（422）", searchErrorMessage(ApiError.Http(422, "Unprocessable")))
        assertEquals("操作过于频繁，已触发 GitHub 限流，请稍后再试", starMessage(ApiError.RateLimited(30)))
    }
}
