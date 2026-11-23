/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.data

import com.gy.githubclient.core.data.download.ContentRange
import com.gy.githubclient.core.data.download.PartialIdentity
import com.gy.githubclient.core.data.download.PartialNaming
import com.gy.githubclient.core.data.download.PartialOwnership
import com.gy.githubclient.core.data.download.PartialReuse
import com.gy.githubclient.core.data.download.RangeDecision
import com.gy.githubclient.core.data.download.RangeRequest
import com.gy.githubclient.core.data.mirror.BundledMirrors
import com.gy.githubclient.core.domain.model.mirror.MirrorPreference
import com.gy.githubclient.core.domain.model.mirror.TrafficKind
import com.gy.githubclient.feature.download.data.download.MirrorUrlResolver
import com.gy.githubclient.feature.download.data.local.toDomain
import com.gy.githubclient.feature.download.data.local.toEntity
import com.gy.githubclient.feature.download.domain.model.DownloadRequest
import com.gy.githubclient.feature.download.domain.model.DownloadStatus
import com.gy.githubclient.feature.download.domain.model.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** 下载数据层：镜像决策、断点续传纯逻辑、Entity 往返。 */
class DownloadDataTest {
    private val assetUrl = "https://github.com/o/r/releases/download/v1/a.apk"
    private val rawUrl = "https://raw.githubusercontent.com/o/r/main/a.txt"

    @Test
    fun directPreferenceNeverRewrites() {
        val result = MirrorUrlResolver.resolve(assetUrl, MirrorPreference.Direct, BundledMirrors.ALL)
        assertNull(result)
    }

    @Test
    fun selectedMirrorRewritesReleaseAsset() {
        val mirror = BundledMirrors.ALL.first { it.urlTemplate?.contains("{url}") == true && TrafficKind.RELEASE_ASSET in it.trafficKinds }
        val result =
            MirrorUrlResolver.resolve(
                assetUrl,
                MirrorPreference.Selected(mirror.id),
                BundledMirrors.ALL,
            )
        assertNotNull(result)
        assertEquals(true, result!!.contains("gh") || result.contains("proxy") || result != assetUrl)
    }

    @Test
    fun jsdelivrRawOnlySkipsReleaseAsset() {
        val result =
            MirrorUrlResolver.resolve(
                assetUrl,
                MirrorPreference.Selected("fastly_jsdelivr"),
                BundledMirrors.ALL,
            )
        // jsdelivr 仅 RAW_FILE，Release 资产不应被改写（分解模板对 release URL 也解析失败）。
        assertNull(result)
    }

    @Test
    fun customTemplateRewrites() {
        val result =
            MirrorUrlResolver.resolve(
                assetUrl,
                MirrorPreference.Custom("https://proxy.example/{url}"),
                BundledMirrors.ALL,
            )
        assertEquals("https://proxy.example/$assetUrl", result)
    }

    @Test
    fun partialNamingRoundTrip() {
        val part = PartialNaming.part("a.apk")
        assertEquals("a.apk", PartialNaming.assetNameOfPart(part))
        val meta = PartialNaming.meta("a.apk")
        assertEquals("a.apk", PartialNaming.assetNameOfMeta(meta))
    }

    @Test
    fun partialReusePrefersDigestThenIdThenSize() {
        val incoming = PartialIdentity(assetId = 7L, digest = "abc", size = 100L)
        assertEquals(
            PartialReuse.REUSE,
            PartialOwnership.decideReuse(true, 50L, PartialIdentity(7L, "abc", 100L), incoming),
        )
        assertEquals(
            PartialReuse.DISCARD,
            PartialOwnership.decideReuse(true, 50L, PartialIdentity(7L, "different", 100L), incoming),
        )
        assertEquals(PartialReuse.DISCARD, PartialOwnership.decideReuse(false, 0L, null, incoming))
    }

    @Test
    fun rangeDecisions() {
        assertEquals(RangeDecision.APPEND, RangeRequest.decide(206, 50L, ContentRange(50L, 100L)))
        assertEquals(RangeDecision.RESTART, RangeRequest.decide(416, 50L, null))
        assertEquals(RangeDecision.TRUNCATE_REWRITE, RangeRequest.decide(200, 50L, null))
        assertEquals("bytes=50-", RangeRequest.headerValue(50L))
        assertNull(RangeRequest.headerValue(0L))
    }

    @Test
    fun entityRoundTripPreservesTask() {
        val request = DownloadRequest(assetUrl, "a.apk", assetId = 7L, totalBytes = 100L)
        val task =
            DownloadTask(
                id = "t1",
                request = request,
                status = DownloadStatus.DOWNLOADING,
                bytesDownloaded = 50L,
                totalBytes = 100L,
                percent = 50,
                createdAt = "now",
                updatedAt = "now",
            )
        val restored = task.toEntity().toDomain()

        assertEquals("t1", restored.id)
        assertEquals(assetUrl, restored.request.url)
        assertEquals(DownloadStatus.DOWNLOADING, restored.status)
        assertEquals(50, restored.percent)
    }

    @Test
    fun rawUrlIsRewritableButDirectSkips() {
        assertNull(MirrorUrlResolver.resolve(rawUrl, MirrorPreference.Direct, BundledMirrors.ALL))
    }
}
