/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.domain

import com.gy.githubclient.feature.profile.domain.model.NotificationUrls
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** 通知 API 地址 → 网页地址（githup 通知跳转修正）纯函数测试。 */
class NotificationUrlsTest {
    @Test
    fun issueApiUrlBecomesWebUrl() {
        assertEquals(
            "https://github.com/o/r/issues/42",
            NotificationUrls.toWebUrl("https://api.github.com/repos/o/r/issues/42", "o/r"),
        )
    }

    @Test
    fun pullApiUrlUsesSingularPullPath() {
        assertEquals(
            "https://github.com/o/r/pull/7",
            NotificationUrls.toWebUrl("https://api.github.com/repos/o/r/pulls/7", "o/r"),
        )
    }

    @Test
    fun commitAndDiscussionAreMapped() {
        assertEquals(
            "https://github.com/o/r/commit/abc123",
            NotificationUrls.toWebUrl("https://api.github.com/repos/o/r/commits/abc123", "o/r"),
        )
        assertEquals(
            "https://github.com/o/r/discussions/9",
            NotificationUrls.toWebUrl("https://api.github.com/repos/o/r/discussions/9", "o/r"),
        )
    }

    @Test
    fun releaseFallsBackToReleaseList() {
        assertEquals(
            "https://github.com/o/r/releases",
            NotificationUrls.toWebUrl("https://api.github.com/repos/o/r/releases/123456", "o/r"),
        )
    }

    @Test
    fun alreadyWebUrlIsKept() {
        assertEquals(
            "https://github.com/o/r/issues/1",
            NotificationUrls.toWebUrl("https://github.com/o/r/issues/1", "o/r"),
        )
    }

    @Test
    fun unknownOrMissingUrlIsHandled() {
        assertNull(NotificationUrls.toWebUrl(null, "o/r"))
        assertEquals(
            "https://github.com/o/r",
            NotificationUrls.toWebUrl("https://api.github.com/repos/o/r/unknown/1", "o/r"),
        )
        assertNull(NotificationUrls.toWebUrl("https://example.com/x", "o/r"))
    }
}
