/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.domain.repository

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.discover.domain.model.DiscoverItem
import com.gy.githubclient.feature.discover.domain.model.FeaturedRepo
import com.gy.githubclient.feature.discover.domain.model.MirrorDownloadLink
import kotlinx.coroutines.flow.Flow

/**
 * 商店发现仓储契约。
 * 数据源 = 本地精选仓库种子 + GitHub Releases REST（拉取每个仓库最新发布）。
 * 镜像/分享能力通过 core 层的实现注入，本契约只暴露解析后的领域结果。
 */
interface DiscoverRepository {
    /** 精选仓库种子清单（产品配置）。 */
    fun featuredRepos(): List<FeaturedRepo>

    /** 观察发现列表（缓存 + 最新 Release 聚合结果）。 */
    fun observeItems(): Flow<List<DiscoverItem>>

    /**
     * 刷新全部精选仓库的最新 Release。
     * @return 成功更新的仓库数量。
     */
    suspend fun refreshReleases(): NetworkResult<Int>

    /** 把 Release Asset 直连地址解析为镜像加速链接。 */
    fun resolveDownloadLink(assetUrl: String): MirrorDownloadLink

    /** 生成仓库/Release 的分享文本（含 deep link）。 */
    fun buildShareText(item: DiscoverItem): String
}
