/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.data

import com.gy.githubclient.core.data.mirror.BundledMirrors
import com.gy.githubclient.core.data.network.MirrorRewriter
import com.gy.githubclient.core.domain.model.mirror.TrafficKind
import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.discover.data.catalog.BundledFeatured
import com.gy.githubclient.feature.discover.data.local.DiscoverDao
import com.gy.githubclient.feature.discover.data.local.toDomain
import com.gy.githubclient.feature.discover.data.local.toEntity
import com.gy.githubclient.feature.discover.data.remote.GitHubReleaseApi
import com.gy.githubclient.feature.discover.domain.model.DiscoverItem
import com.gy.githubclient.feature.discover.domain.model.FeaturedRepo
import com.gy.githubclient.feature.discover.domain.model.MirrorDownloadLink
import com.gy.githubclient.feature.discover.domain.model.ReleaseInfo
import com.gy.githubclient.feature.discover.domain.model.TagInfo
import com.gy.githubclient.feature.discover.domain.repository.DiscoverRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock

/**
 * 发现仓储实现：
 * - 种子清单（BundledFeatured）为发现源；
 * - 刷新时并发拉取每个仓库最新 Release，单个失败保留种子卡片（latestRelease=null）；
 * - 结果落 Room（单一数据源），UI 通过 observeItems 观察；
 * - 镜像解析复用 core 的 MirrorRewriter + BundledMirrors（默认选第一个可用社区镜像）。
 */
@Suppress("TooGenericExceptionCaught")
class DiscoverRepositoryImpl(
    private val dao: DiscoverDao,
    private val api: GitHubReleaseApi,
) : DiscoverRepository {
    override fun featuredRepos(): List<FeaturedRepo> = BundledFeatured.ALL

    override fun observeItems(): Flow<List<DiscoverItem>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun refreshReleases(): NetworkResult<Int> =
        withContext(Dispatchers.IO) {
            val seeds = BundledFeatured.ALL
            // 先用种子建立/补齐底稿，保证即使全部 Release 拉取失败也有内容展示。
            val now = Clock.System.now().toString()
            val baseItems =
                seeds.map { seed ->
                    DiscoverItem(featured = seed, latestRelease = null, fetchedAt = null)
                }
            dao.upsertAll(baseItems.map { it.toEntity() })

            // 并发拉取，有限并发度（4）避免触发 GitHub 限流；单仓库失败不影响整体。
            val updated =
                coroutineScope {
                    seeds
                        .chunked(CONCURRENCY)
                        .flatMap { chunk ->
                            chunk
                                .map { seed ->
                                    async {
                                        val release =
                                            when (val result = api.latestRelease(seed.owner, seed.name)) {
                                                is NetworkResult.Success -> result.data
                                                is NetworkResult.Failure -> null
                                            }
                                        DiscoverItem(featured = seed, latestRelease = release, fetchedAt = now)
                                    }
                                }.awaitAll()
                        }
                }
            dao.upsertAll(updated.map { it.toEntity() })

            val successCount = updated.count { it.latestRelease != null }
            if (successCount == 0 && seeds.isNotEmpty()) {
                // 全部失败：若确为限流则透传错误，否则以空成功返回（本地种子已落库）。
                NetworkResult.Failure(ApiError.Network("全部精选仓库的 Release 拉取失败"))
            } else {
                NetworkResult.Success(successCount)
            }
        }

    override suspend fun listReleases(
        owner: String,
        name: String,
        page: Int,
        perPage: Int,
        includePrerelease: Boolean,
    ): NetworkResult<List<ReleaseInfo>> =
        when (val result = api.listReleases(owner, name, page, perPage)) {
            is NetworkResult.Success -> {
                val visible =
                    result.data.filter { release ->
                        !release.isDraft && (includePrerelease || !release.isPrerelease)
                    }
                NetworkResult.Success(visible)
            }

            is NetworkResult.Failure -> result
        }

    override suspend fun listTags(
        owner: String,
        name: String,
        perPage: Int,
    ): NetworkResult<List<TagInfo>> = api.listTags(owner, name, perPage)

    override fun resolveDownloadLink(assetUrl: String): MirrorDownloadLink {
        if (!MirrorRewriter.shouldRewrite(assetUrl)) {
            return MirrorDownloadLink(assetUrl, null, null, false)
        }
        val kind = MirrorRewriter.classify(assetUrl) ?: TrafficKind.RELEASE_ASSET
        val mirror =
            BundledMirrors.ALL.firstOrNull {
                it.urlTemplate != null && kind in it.trafficKinds
            }
        val template = mirror?.urlTemplate
        val accelerated = template?.let { MirrorRewriter.applyTemplate(it, assetUrl) }
        return MirrorDownloadLink(
            originalUrl = assetUrl,
            acceleratedUrl = accelerated,
            mirrorName = if (accelerated != null) mirror?.name else null,
            isAccelerated = accelerated != null,
        )
    }

    override fun buildShareText(item: DiscoverItem): String {
        val release = item.latestRelease
        val deepLink = "githubclient://repo/${item.featured.owner}/${item.featured.name}"
        val header = "${item.featured.fullName} — ${item.featured.tagline}"
        val releaseLine = release?.let { "\n最新版本：${it.tagName}" } ?: ""
        val assetLine =
            release?.assets?.firstOrNull()?.let { "\n下载：${it.downloadUrl}" } ?: ""
        return "$header\n$deepLink$releaseLine$assetLine"
    }

    private companion object {
        const val CONCURRENCY = 4
    }
}
