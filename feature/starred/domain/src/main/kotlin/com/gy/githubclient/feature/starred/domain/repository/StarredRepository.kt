/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.starred.domain.repository

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.domain.model.Repository
import com.gy.githubclient.feature.starred.domain.model.StarredFilter
import com.gy.githubclient.feature.starred.domain.model.StarredPage
import kotlinx.coroutines.flow.Flow

/**
 * 星标仓库仓库契约：UI 观察本地缓存流，刷新走统一网络层并回填缓存。
 * 复用 repo feature 的 [Repository] 领域模型，避免重复定义仓库结构。
 */
interface StarredRepository {
    /** 观察本地缓存的星标仓库列表。 */
    fun observeStarred(): Flow<List<Repository>>

    /**
     * 刷新星标列表并回填缓存。
     * @param append 为 true 时追加下一页；false 时替换第一页。
     */
    suspend fun refreshStarred(
        filter: StarredFilter,
        append: Boolean = false,
    ): NetworkResult<StarredPage>

    /** 移除星标（unstar）；成功时同步从缓存删除。 */
    suspend fun removeStar(
        owner: String,
        name: String,
    ): NetworkResult<Unit>

    /** 清空缓存（登出时调用）。 */
    suspend fun clearCache()
}
