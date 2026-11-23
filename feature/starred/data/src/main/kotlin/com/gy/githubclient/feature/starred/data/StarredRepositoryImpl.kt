/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.starred.data

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.domain.model.Repository
import com.gy.githubclient.feature.starred.data.local.StarredDao
import com.gy.githubclient.feature.starred.data.local.toDomain
import com.gy.githubclient.feature.starred.data.local.toEntity
import com.gy.githubclient.feature.starred.data.remote.GitHubStarredApi
import com.gy.githubclient.feature.starred.domain.model.StarredFilter
import com.gy.githubclient.feature.starred.domain.model.StarredPage
import com.gy.githubclient.feature.starred.domain.repository.StarredRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 星标仓库实现（Room 为单一数据源）：刷新回填缓存，失败保留旧数据。 */
class StarredRepositoryImpl(
    private val dao: StarredDao,
    private val api: GitHubStarredApi,
) : StarredRepository {
    override fun observeStarred(): Flow<List<Repository>> = dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun refreshStarred(
        filter: StarredFilter,
        append: Boolean,
    ): NetworkResult<StarredPage> =
        when (val result = api.listStarred(filter)) {
            is NetworkResult.Failure -> NetworkResult.Failure(result.error)

            is NetworkResult.Success -> {
                val items = result.data
                val entities = items.map { it.toEntity(bucketOf(filter), filter.page) }
                if (append) {
                    dao.upsertAll(entities)
                } else {
                    dao.replaceBucket(bucketOf(filter), entities)
                }
                NetworkResult.Success(
                    StarredPage(
                        loaded = items.size,
                        hasMore = items.size >= filter.perPage,
                    ),
                )
            }
        }

    override suspend fun removeStar(
        owner: String,
        name: String,
    ): NetworkResult<Unit> =
        when (val result = api.removeStar(owner, name)) {
            is NetworkResult.Success -> {
                dao.deleteByFullName("$owner/$name")
                NetworkResult.Success(Unit)
            }

            is NetworkResult.Failure -> NetworkResult.Failure(result.error)
        }

    override suspend fun clearCache() = dao.clearAll()

    private fun bucketOf(filter: StarredFilter): String = filter.sort.name
}
