/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.presentation

import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.feature.discover.domain.model.MirrorDownloadLink
import com.gy.githubclient.feature.discover.domain.model.ReleaseAsset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 多版本列表下载请求构造与错误文案（v0.8.0 M14/M15）。 */
class ReleasePresentationSharedTest {
    private val asset =
        ReleaseAsset(
            id = 42L,
            name = "app-release.apk",
            size = 2048L,
            downloadUrl = "https://github.com/o/r/releases/download/v1/app-release.apk",
            contentType = "application/vnd.android.package-archive",
            sha256 = "abc123",
        )

    @Test
    fun downloadRequestCarriesMirrorShaAndProvenance() {
        val link = MirrorDownloadLink(asset.downloadUrl, "https://gh-proxy.com/x", "gh-proxy.com", true)

        val req = buildAssetDownloadRequest(asset, "o", "r", "v1", link)

        assertEquals(asset.downloadUrl, req.url)
        assertEquals("app-release.apk", req.fileName)
        assertEquals("o", req.repoOwner)
        assertEquals("r", req.repoName)
        assertEquals("v1", req.releaseTag)
        assertEquals(42L, req.assetId)
        assertEquals(2048L, req.totalBytes)
        assertTrue(req.useMirror)
        assertEquals("abc123", req.expectedSha256)
    }

    @Test
    fun directLinkDisablesMirrorAndBlankNameFallsBack() {
        val blankAsset = asset.copy(name = "")
        val link = MirrorDownloadLink(asset.downloadUrl, null, null, false)

        val req = buildAssetDownloadRequest(blankAsset, "o", "r", "v1", link)

        assertEquals("download.bin", req.fileName)
        assertNull(req.suggestedFileName)
        assertFalse(req.useMirror)
    }

    @Test
    fun errorMessagesAreChineseAndSpecific() {
        assertTrue(discoverErrorMessage(ApiError.Unauthorized).contains("重新登录"))
        assertTrue(discoverErrorMessage(ApiError.NotFound).contains("未找到"))
        assertTrue(discoverErrorMessage(ApiError.Http(500, "Server Error")).contains("500"))
    }
}
