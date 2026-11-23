/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.domain

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.discover.domain.model.DiscoverCategory
import com.gy.githubclient.feature.discover.domain.model.DiscoverItem
import com.gy.githubclient.feature.discover.domain.model.FeaturedRepo
import com.gy.githubclient.feature.discover.domain.model.MirrorDownloadLink
import com.gy.githubclient.feature.discover.domain.model.ReleaseInfo
import com.gy.githubclient.feature.discover.domain.repository.DiscoverRepository
import com.gy.githubclient.feature.discover.domain.usecase.BuildShareTextUseCase
import com.gy.githubclient.feature.discover.domain.usecase.GetFeaturedReposUseCase
import com.gy.githubclient.feature.discover.domain.usecase.ObserveDiscoverUseCase
import com.gy.githubclient.feature.discover.domain.usecase.RefreshDiscoverUseCase
import com.gy.githubclient.feature.discover.domain.usecase.ResolveDownloadLinkUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 发现领域层：分类解析、种子清单、用例委托、镜像与分享。 */
class DiscoverDomainTest {
    private val repo =
        object : DiscoverRepository {
            override fun featuredRepos() = SEEDS

            override fun observeItems(): Flow<List<DiscoverItem>> = flowOf(emptyList())

            override suspend fun refreshReleases(): NetworkResult<Int> = NetworkResult.Success(SEEDS.size)

            override fun resolveDownloadLink(assetUrl: String) =
                MirrorDownloadLink(
                    originalUrl = assetUrl,
                    acceleratedUrl = "https://gh-proxy.com/$assetUrl",
                    mirrorName = "gh-proxy.com",
                    isAccelerated = true,
                )

            override fun buildShareText(item: DiscoverItem): String =
                "${item.featured.fullName} · githubclient://repo/${item.featured.fullName}"
        }

    @Test
    fun categoryParsesCaseInsensitively() {
        assertEquals(DiscoverCategory.ANDROID, DiscoverCategory.fromWire("android"))
        assertEquals(DiscoverCategory.DEVELOPER, DiscoverCategory.fromWire("DEVELOPER"))
        assertEquals(DiscoverCategory.ALL, DiscoverCategory.fromWire("not-a-category"))
    }

    @Test
    fun featuredReposUseCaseReturnsSeeds() {
        val list = GetFeaturedReposUseCase(repo)()

        assertEquals(SEEDS.size, list.size)
        assertTrue(list.all { it.fullName.contains('/') })
        assertTrue(list.any { it.category == DiscoverCategory.ANDROID })
    }

    @Test
    fun observeUseCaseDelegatesToRepository() =
        runTest {
            val flow = ObserveDiscoverUseCase(repo)()
            assertEquals(emptyList<DiscoverItem>(), flow.first())
        }

    @Test
    fun refreshUseCaseReturnsUpdatedCount() =
        runTest {
            val result = RefreshDiscoverUseCase(repo)()
            assertTrue(result is NetworkResult.Success)
            assertEquals(SEEDS.size, (result as NetworkResult.Success).data)
        }

    @Test
    fun resolveLinkAndShareTextArePure() {
        val url = "https://github.com/o/r/releases/download/v1/a.apk"
        val link = ResolveDownloadLinkUseCase(repo)(url)
        assertTrue(link.isAccelerated)
        assertEquals(url, link.originalUrl)

        val item = DiscoverItem(SEEDS.first(), ReleaseInfo("v1", "r", null, "https://github.com/o/r", null, false, false), null)
        assertTrue(BuildShareTextUseCase(repo)(item).contains("githubclient://repo/"))
    }

    private companion object {
        val SEEDS =
            listOf(
                FeaturedRepo("square", "retrofit", DiscoverCategory.DEVELOPER, "类型安全的 HTTP 客户端"),
                FeaturedRepo("android", "nowinandroid", DiscoverCategory.ANDROID, "Android 现代开发范例"),
                FeaturedRepo("home-assistant", "frontend", DiscoverCategory.UTILITY, "智能家居控制面板"),
            )
    }
}
