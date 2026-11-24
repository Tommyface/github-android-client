/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.6.0 M5/M8 回归：digest 解析与预发布列表挑选。 */
class UpdateCheckerParsersTest {
    private fun asset(
        name: String = "app.apk",
        digest: String? = null,
        size: Long = 0,
    ): ReleaseAssetDto = ReleaseAssetDto(name = name, downloadUrl = "https://example.com/$name", size = size, digest = digest)

    @Test
    fun officialSha256AcceptsValidDigest() {
        val hex = "ab".repeat(32)
        assertEquals(hex, asset(digest = "sha256:$hex").officialSha256())
        assertEquals(hex, asset(digest = "SHA256:$hex").officialSha256())
        // 大写输入归一化为小写
        assertEquals(hex, asset(digest = "sha256:" + hex.uppercase()).officialSha256())
    }

    @Test
    fun officialSha256RejectsInvalidDigests() {
        assertNull(asset(digest = null).officialSha256())
        assertNull(asset(digest = "sha512:ab".repeat(2)).officialSha256())
        assertNull(asset(digest = "sha256:xyz").officialSha256()) // 长度不足
        assertNull(asset(digest = "sha256:${"ff".repeat(31)}").officialSha256()) // 63 位
    }

    @Test
    fun isApkOrZipMatchesAssetNames() {
        assertTrue(asset(name = "github-android-client-debug-v0.6.0.apk").isApkOrZip())
        assertTrue(asset(name = "bundle.zip").isApkOrZip())
        assertTrue(!asset(name = "sources.jar").isApkOrZip())
    }

    @Test
    fun pickNewestReleasePrefersHighestVersionCode() {
        val releases =
            listOf(
                ReleaseLatestDto(tagName = "v0.5.0", body = "versionCode: 8"),
                ReleaseLatestDto(tagName = "v0.6.0-rc1", prerelease = true, body = "versionCode: 10"),
                ReleaseLatestDto(tagName = "v0.6.0", body = "versionCode: 9"),
            )
        assertEquals("v0.6.0-rc1", pickNewestRelease(releases)?.tagName)
    }

    @Test
    fun pickNewestReleaseFallsBackToPublishedAt() {
        val releases =
            listOf(
                ReleaseLatestDto(tagName = "v0.4.0", publishedAt = "2026-01-01T00:00:00Z"),
                ReleaseLatestDto(tagName = "v0.5.0", publishedAt = "2026-06-01T00:00:00Z"),
            )
        assertEquals("v0.5.0", pickNewestRelease(releases)?.tagName)
    }

    @Test
    fun pickNewestReleaseHandlesEmptyAndUnmarked() {
        assertEquals(0, pickNewestRelease(emptyList())?.let { 0 } ?: 0)
        val unmarked = listOf(ReleaseLatestDto(tagName = "v1"), ReleaseLatestDto(tagName = "v2"))
        assertEquals("v1", pickNewestRelease(unmarked)?.tagName) // 无 published_at 取第一个
    }
}
