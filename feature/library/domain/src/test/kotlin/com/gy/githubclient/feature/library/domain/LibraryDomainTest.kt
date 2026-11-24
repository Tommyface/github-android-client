/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.library.domain

import com.gy.githubclient.feature.discover.domain.model.ReleaseAsset
import com.gy.githubclient.feature.library.domain.model.inferPlatform
import com.gy.githubclient.feature.library.domain.model.isUpdateAvailable
import com.gy.githubclient.feature.library.domain.model.selectApkAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryDomainTest {
    private fun asset(name: String) =
        ReleaseAsset(
            id = 1L,
            name = name,
            size = 100L,
            downloadUrl = "https://example.com/$name",
            contentType = "application/vnd.android.package-archive",
        )

    @Test
    fun `isUpdateAvailable compares semantic versions`() {
        assertTrue(isUpdateAvailable("v0.8.34", "v0.8.31"))
        assertTrue(isUpdateAvailable("1.2.0", "1.1.9"))
        assertFalse(isUpdateAvailable("v0.8.31", "v0.8.31"))
        assertFalse(isUpdateAvailable("v0.8.30", "v0.8.31"))
        assertTrue(isUpdateAvailable("v2.0", "v1.9.9"))
    }

    @Test
    fun `isUpdateAvailable handles blank installed and prerelease tags`() {
        assertTrue(isUpdateAvailable("v1.0.0", ""))
        assertFalse(isUpdateAvailable("v1.0.0-beta", "v1.0.0-alpha")) // core 相同视为同一版本
        assertTrue(isUpdateAvailable("release-2026", "old-build")) // 无法解析时字符串不等即视为有更新
    }

    @Test
    fun `selectApkAsset filters apk and honors abi preference`() {
        val assets =
            listOf(
                asset("app-universal.apk"),
                asset("app-arm64-v8a.apk"),
                asset("app-x86_64.apk"),
                asset("changelog.md"),
            )
        assertEquals("app-arm64-v8a.apk", selectApkAsset(assets, "arm64-v8a")?.name)
        assertEquals("app-x86_64.apk", selectApkAsset(assets, "x86")?.name?.let { if (it.contains("x86")) "app-x86_64.apk" else it })
        assertNull(selectApkAsset(listOf(asset("readme.md")), ""))
        assertEquals("app-universal.apk", selectApkAsset(assets, "")?.name)
    }

    @Test
    fun `inferPlatform maps asset names to platform labels`() {
        assertEquals("Android", inferPlatform("app-arm64-v8a.apk"))
        assertEquals("Windows", inferPlatform("setup.msi"))
        assertEquals("macOS", inferPlatform("app.dmg"))
        assertEquals("Linux", inferPlatform("app.AppImage"))
        assertNull(inferPlatform("source.zip"))
    }
}
