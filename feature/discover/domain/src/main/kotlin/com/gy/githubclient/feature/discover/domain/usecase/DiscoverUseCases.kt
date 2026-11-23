/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.domain.usecase

import com.gy.githubclient.feature.discover.domain.model.DiscoverItem
import com.gy.githubclient.feature.discover.domain.model.FeaturedRepo
import com.gy.githubclient.feature.discover.domain.model.MirrorDownloadLink
import com.gy.githubclient.feature.discover.domain.repository.DiscoverRepository
import kotlinx.coroutines.flow.Flow

/** 观察发现列表（数据库为单一数据源）。 */
class ObserveDiscoverUseCase(
    private val repository: DiscoverRepository,
) {
    operator fun invoke(): Flow<List<DiscoverItem>> = repository.observeItems()
}

/** 拉取精选仓库最新 Release。 */
class RefreshDiscoverUseCase(
    private val repository: DiscoverRepository,
) {
    suspend operator fun invoke() = repository.refreshReleases()
}

/** 获取精选仓库种子清单。 */
class GetFeaturedReposUseCase(
    private val repository: DiscoverRepository,
) {
    operator fun invoke(): List<FeaturedRepo> = repository.featuredRepos()
}

/** 解析 Release Asset 的镜像下载链接。 */
class ResolveDownloadLinkUseCase(
    private val repository: DiscoverRepository,
) {
    operator fun invoke(assetUrl: String): MirrorDownloadLink = repository.resolveDownloadLink(assetUrl)
}

/** 生成分享文本。 */
class BuildShareTextUseCase(
    private val repository: DiscoverRepository,
) {
    operator fun invoke(item: DiscoverItem): String = repository.buildShareText(item)
}
