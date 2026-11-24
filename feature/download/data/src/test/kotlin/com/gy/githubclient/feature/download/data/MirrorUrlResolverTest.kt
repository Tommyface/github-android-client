/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.data

import com.gy.githubclient.core.data.mirror.BundledMirrors
import com.gy.githubclient.core.domain.model.mirror.MirrorPreference
import com.gy.githubclient.feature.download.data.download.MirrorUrlResolver
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 回归（v0.5.3 M1）：下载失败轮换候选链——选定镜像 → 池内其他镜像（按延迟）→ 直连。 */
class MirrorUrlResolverTest {
    private val releaseUrl = "https://github.com/Tommyface/github-android-client/releases/download/v0.5.3/app.apk"
    private val catalog = BundledMirrors.ALL

    @Test
    fun selectedMirrorYieldsPoolFallbackThenDirect() {
        val latencies = mapOf("ghfast_top" to 120, "gh_proxy_com" to 300, "moeyy_xyz" to 80)
        val candidates =
            MirrorUrlResolver.candidateUrls(
                releaseUrl,
                MirrorPreference.Selected("ghfast_top"),
                catalog,
                latencies,
            )
        assertEquals("首选镜像 ghfast.top 的改写前缀", "https://ghfast.top", firstTemplatePrefix(candidates))
        assertEquals(
            "首选 + 4 个全量代理镜像 + 直连（jsdelivr 仅 raw 不参与）",
            6,
            candidates.size,
        )
        assertEquals("直连兜底在最后", releaseUrl, candidates.last())
        assertTrue(
            "按延迟升序：moeyy(80) 先于 gh-proxy.com(300)",
            candidates.indexOf("https://github.moeyy.xyz/$releaseUrl") <
                candidates.indexOf("https://gh-proxy.com/$releaseUrl"),
        )
        assertTrue("jsdelivr 不支持 release 资产，不参与候选", candidates.none { it.contains("jsdelivr") })
    }

    @Test
    fun directPreferenceNeverBorrowsMirrors() {
        val candidates = MirrorUrlResolver.candidateUrls(releaseUrl, MirrorPreference.Direct, catalog, emptyMap())
        assertEquals(listOf(releaseUrl), candidates)
    }

    @Test
    fun customTemplateFallsBackToBundledPoolThenDirect() {
        val candidates =
            MirrorUrlResolver.candidateUrls(
                releaseUrl,
                MirrorPreference.Custom("https://worker.example.com/{url}"),
                catalog,
                emptyMap(),
            )
        assertEquals("https://worker.example.com/$releaseUrl", candidates.first())
        assertEquals("自定义 + 5 个全量代理镜像 + 直连", 7, candidates.size)
        assertEquals(releaseUrl, candidates.last())
    }

    @Test
    fun rawFileIncludesJsdelivrByLatency() {
        val rawUrl = "https://raw.githubusercontent.com/octocat/Hello-World/master/README"
        val latencies = mapOf("fastly_jsdelivr" to 50, "ghfast_top" to 100)
        val candidates =
            MirrorUrlResolver.candidateUrls(
                rawUrl,
                MirrorPreference.Selected("ghfast_top"),
                catalog,
                latencies,
            )
        val jsdelivrUrl = "https://fastly.jsdelivr.net/gh/octocat/Hello-World@master/README"
        assertTrue("raw 流量下 jsdelivr 应参与候选", jsdelivrUrl in candidates)
        assertTrue(
            "jsdelivr(50ms) 应先于无测速数据的镜像（默认排最后）",
            candidates.indexOf(jsdelivrUrl) < candidates.indexOf("https://gh.api.99988866.xyz/$rawUrl"),
        )
    }

    private fun firstTemplatePrefix(candidates: List<String>): String = candidates.first().substringBefore("/https")
}
