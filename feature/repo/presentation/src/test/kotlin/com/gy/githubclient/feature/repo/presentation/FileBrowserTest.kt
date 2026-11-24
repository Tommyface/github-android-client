/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

/** 文件浏览器纯函数（v0.9.0 M18）：大小格式化与目录路径栈。 */
class FileBrowserTest {
    @Test
    fun formatFileSizeHandlesAllBuckets() {
        assertEquals("—", formatFileSize(0))
        assertEquals("512 B", formatFileSize(512))
        assertEquals("2 KB", formatFileSize(2048))
        assertEquals("1 MB", formatFileSize(1024 * 1024))
    }

    @Test
    fun childAndParentPathRoundTrip() {
        assertEquals("lib", childPath("", "lib"))
        assertEquals("app/lib", childPath("app", "lib"))

        assertEquals("", parentPath(""))
        assertEquals("", parentPath("lib"))
        assertEquals("app", parentPath("app/lib"))
        assertEquals("app/src", parentPath("app/src/main"))
    }

    @Test
    fun breadcrumbsMatchCurrentPath() {
        val root = FileBrowserUiState(currentPath = "")
        assertEquals(listOf("/"), root.breadcrumbs)

        val nested = FileBrowserUiState(currentPath = "app/src")
        assertEquals(listOf("/", "app", "src"), nested.breadcrumbs)
    }
}
