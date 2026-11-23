/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.starred.domain.usecase

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.domain.model.Repository
import com.gy.githubclient.feature.starred.domain.model.StarredFilter
import com.gy.githubclient.feature.starred.domain.model.StarredPage
import com.gy.githubclient.feature.starred.domain.repository.StarredRepository
import kotlinx.coroutines.flow.Flow

/** 观察星标仓库缓存。 */
class ObserveStarredUseCase(
    private val repository: StarredRepository,
) {
    operator fun invoke(): Flow<List<Repository>> = repository.observeStarred()
}

/** 刷新星标列表；[append] 为 true 时分页追加。 */
class RefreshStarredUseCase(
    private val repository: StarredRepository,
) {
    suspend operator fun invoke(
        filter: StarredFilter,
        append: Boolean = false,
    ): NetworkResult<StarredPage> = repository.refreshStarred(filter, append)
}

/** 移除星标。 */
class RemoveStarUseCase(
    private val repository: StarredRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
    ): NetworkResult<Unit> = repository.removeStar(owner, name)
}

/** 清空缓存（登出时）。 */
class ClearStarredCacheUseCase(
    private val repository: StarredRepository,
) {
    suspend operator fun invoke() = repository.clearCache()
}
