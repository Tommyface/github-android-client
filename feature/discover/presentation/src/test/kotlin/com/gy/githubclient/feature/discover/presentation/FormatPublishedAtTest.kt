/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

/** Release 详情页发布时间格式化：ISO 8601 → 可读本地展示。 */
class FormatPublishedAtTest {
    @Test
    fun formatsIsoTimestampToReadableForm() {
        assertEquals("2023-07-13 11:09 UTC", formatPublishedAt("2023-07-13T11:09:53Z"))
        assertEquals("2026-11-24 03:07 UTC", formatPublishedAt("2026-11-24T03:07:41Z"))
    }

    @Test
    fun keepsUnparseableInputAsIs() {
        assertEquals("刚刚发布", formatPublishedAt("刚刚发布"))
        assertEquals("", formatPublishedAt(""))
    }

    @Test
    fun handlesOffsetsAndFractionalSeconds() {
        // 带毫秒与偏移的变体同样可读化。
        assertEquals("2024-01-02 08:30 UTC", formatPublishedAt("2024-01-02T08:30:00.123Z"))
    }
}
