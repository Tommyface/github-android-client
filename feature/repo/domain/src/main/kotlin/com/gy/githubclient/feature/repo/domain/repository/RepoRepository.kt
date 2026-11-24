/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain.repository

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.domain.model.Branch
import com.gy.githubclient.feature.repo.domain.model.RepoFilter
import com.gy.githubclient.feature.repo.domain.model.Repository
import kotlinx.coroutines.flow.Flow

/**
 * 仓库仓库契约：UI 只观察缓存流，刷新由网络层回填缓存。
 *
 * - [observeRepositories] 暴露 Room 缓存的 Flow，离线也有数据。
 * - [refreshRepositories] 走统一网络层 + ETag 条件请求；304 时不重写缓存。
 * 领域层只定义契约，具体实现位于 data 层。
 */
interface RepoRepository {
    /** 观察当前筛选条件下的仓库缓存列表。 */
    fun observeRepositories(filter: RepoFilter): Flow<List<Repository>>

    /**
     * 刷新仓库列表并回填缓存。
     * @param append 为 true 时追加下一页；false 时替换第一页。
     * @return 本次实际写入的条数（304 未修改为 0）。
     */
    suspend fun refreshRepositories(
        filter: RepoFilter,
        append: Boolean = false,
    ): NetworkResult<Int>

    /** 获取单个仓库详情（含 star 状态）。 */
    suspend fun getRepository(
        owner: String,
        name: String,
    ): NetworkResult<Repository>

    /** 列出仓库分支。 */
    suspend fun listBranches(
        owner: String,
        name: String,
    ): NetworkResult<List<Branch>>

    /** star / unstar；成功与否以 [NetworkResult] 表达。 */
    suspend fun setStarred(
        owner: String,
        name: String,
        starred: Boolean,
    ): NetworkResult<Unit>

    /** 查询当前用户是否已星标该仓库（404 视为未星标，返回 false 而非错误）。 */
    suspend fun isStarred(
        owner: String,
        name: String,
    ): NetworkResult<Boolean>

    /** Fork 仓库到当前用户账号（202 异步；返回 fork 后的仓库对象）。 */
    suspend fun fork(
        owner: String,
        name: String,
    ): NetworkResult<Repository>

    /** 清空本地仓库缓存（登出时调用，防止串号）。 */
    suspend fun clearCache()
}
