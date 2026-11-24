/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain.usecase

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.domain.model.Branch
import com.gy.githubclient.feature.repo.domain.model.RepoFilter
import com.gy.githubclient.feature.repo.domain.model.Repository
import com.gy.githubclient.feature.repo.domain.repository.RepoRepository
import kotlinx.coroutines.flow.Flow

/** 观察仓库缓存列表。 */
class ObserveRepositoriesUseCase(
    private val repository: RepoRepository,
) {
    operator fun invoke(filter: RepoFilter): Flow<List<Repository>> =
        repository.observeRepositories(filter)
}

/** 刷新仓库列表；[append] 为 true 时分页追加。 */
class RefreshRepositoriesUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        filter: RepoFilter,
        append: Boolean = false,
    ): NetworkResult<Int> = repository.refreshRepositories(filter, append)
}

/** 获取单个仓库详情。 */
class GetRepositoryUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
    ): NetworkResult<Repository> = repository.getRepository(owner, name)
}

/** 列出仓库分支。 */
class ListBranchesUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
    ): NetworkResult<List<Branch>> = repository.listBranches(owner, name)
}

/** star / unstar。 */
class SetStarredUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
        starred: Boolean,
    ): NetworkResult<Unit> = repository.setStarred(owner, name, starred)
}

/** 查询是否已星标（404 视为未星标）。 */
class IsStarredUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
    ): NetworkResult<Boolean> = repository.isStarred(owner, name)
}

/** Fork 到当前用户账号（202 异步）。 */
class ForkRepositoryUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
    ): NetworkResult<Repository> = repository.fork(owner, name)
}

/** 清空缓存（登出时）。 */
class ClearRepoCacheUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke() = repository.clearCache()
}
