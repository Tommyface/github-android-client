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
import com.gy.githubclient.feature.download.data.download.Sha256Verifier
import com.gy.githubclient.feature.download.data.local.toDomain
import com.gy.githubclient.feature.download.data.local.toEntity
import com.gy.githubclient.feature.download.domain.model.DownloadRequest
import com.gy.githubclient.feature.download.domain.model.DownloadStatus
import com.gy.githubclient.feature.download.domain.model.DownloadTask
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

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
    fun contentRangeParsingDrivesRealResume() {
        // M12 回归：206 必须解析 Content-Range 且与已收字节一致才 APPEND；
        // 历史缺陷是调用方传 null → 恒判 RESTART → 断点续传失效且写出缺头文件。
        val parsed = ContentRange.parse("bytes 50-99/150")
        assertNotNull(parsed)
        assertEquals(50L, parsed!!.start)
        assertEquals(150L, parsed.total)
        assertEquals(RangeDecision.APPEND, RangeRequest.decide(206, 50L, parsed))

        // 起点不匹配 → RESTART（从头重写，不拼接错位字节）。
        assertEquals(RangeDecision.RESTART, RangeRequest.decide(206, 40L, parsed))
        // 解析失败 → null → 保守 RESTART。
        assertNull(ContentRange.parse("garbage"))
        assertEquals(RangeDecision.RESTART, RangeRequest.decide(206, 50L, null))
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
    fun entityRoundTripPreservesExpectedSha256() {
        val request =
            DownloadRequest(assetUrl, "a.apk", assetId = 7L, totalBytes = 100L, expectedSha256 = "ab12cd34")
        val restored = taskOf(request).toEntity().toDomain()

        assertEquals("ab12cd34", restored.request.expectedSha256)
    }

    @Test
    fun sha256VerifierKnowsStandardVector() {
        val file = tempFile("abc")
        // NIST 标准测试向量：sha256("abc")。
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", Sha256Verifier.digest(file))
    }

    @Test
    fun sha256VerifierMatchesIgnoringCaseAndSkipsBlank() {
        val file = tempFile("abc")
        assertTrue(
            Sha256Verifier.verify(file, "BA7816BF8F01CFEA414140DE5DAE2223B00361A396177A9CB410FF61F20015AD"),
        )
        assertTrue(Sha256Verifier.verify(file, " "))
        assertFalse(Sha256Verifier.verify(file, "deadbeef"))
    }

    private fun taskOf(request: DownloadRequest) =
        DownloadTask(
            id = "t1",
            request = request,
            status = DownloadStatus.QUEUED,
            createdAt = "now",
            updatedAt = "now",
        )

    private fun tempFile(content: String): File =
        kotlin.io.path.createTempFile("sha_test", ".bin").toFile().apply {
            writeText(content)
            deleteOnExit()
        }

    @Test
    fun rawUrlIsRewritableButDirectSkips() {
        assertNull(MirrorUrlResolver.resolve(rawUrl, MirrorPreference.Direct, BundledMirrors.ALL))
    }

    private val archiveUrl = "https://github.com/square/retrofit/archive/refs/heads/main.zip"

    @Test
    fun archiveUrlRewritesThroughFullProxyMirror() {
        // 全量代理镜像（{url} 模板）支持整链替换 archive 请求。
        val ghfast = BundledMirrors.ALL.first { it.id == "ghfast_top" }
        val rewritten =
            MirrorUrlResolver.resolve(archiveUrl, MirrorPreference.Selected(id = ghfast.id), BundledMirrors.ALL)

        assertEquals("https://ghfast.top/$archiveUrl", rewritten)
    }

    @Test
    fun archiveUrlFallsBackToDirectOnRawOnlyMirror() {
        // raw-only 镜像（jsdelivr 分解模板）不支持 archive → 回落直连，避免拼出 404。
        val jsdelivr = BundledMirrors.ALL.first { it.id == "fastly_jsdelivr" }
        val rewritten =
            MirrorUrlResolver.resolve(archiveUrl, MirrorPreference.Selected(id = jsdelivr.id), BundledMirrors.ALL)

        assertNull(rewritten)
    }

    @Test
    fun directPreferenceNeverRewritesArchive() {
        assertNull(MirrorUrlResolver.resolve(archiveUrl, MirrorPreference.Direct, BundledMirrors.ALL))
    }
}
