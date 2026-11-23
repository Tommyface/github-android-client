/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain

import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.domain.model.Branch
import com.gy.githubclient.feature.repo.domain.model.RepoFilter
import com.gy.githubclient.feature.repo.domain.model.Repository
import com.gy.githubclient.feature.repo.domain.repository.RepoRepository
import com.gy.githubclient.feature.repo.domain.usecase.RefreshRepositoriesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.SetStarredUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 仓库领域层纯逻辑测试，不依赖 Android。 */
class RepoDomainTest {
    @Test
    fun nextPageIncrementsPageOnly() {
        val filter = RepoFilter(page = 1)
        val next = filter.nextPage()

        assertEquals(2, next.page)
        assertEquals(filter.type, next.type)
        assertEquals(filter.sort, next.sort)
    }

    @Test
    fun refreshFailureIsPropagated() =
        runTest {
            val repo = StubRepoRepository(refreshResult = NetworkResult.Failure(ApiError.NotFound))
            val result = RefreshRepositoriesUseCase(repo)(RepoFilter())

            assertTrue(result is NetworkResult.Failure)
            assertEquals(ApiError.NotFound, (result as NetworkResult.Failure).error)
        }

    @Test
    fun setStarredDelegatesOwnerAndName() =
        runTest {
            val repo = StubRepoRepository()
            val result = SetStarredUseCase(repo)("octocat", "hello", true)

            assertTrue(result is NetworkResult.Success)
            assertEquals("octocat", repo.lastOwner)
            assertEquals("hello", repo.lastName)
            assertEquals(true, repo.lastStarred)
        }
}

/** 测试替身。 */
private class StubRepoRepository(
    private val refreshResult: NetworkResult<Int> = NetworkResult.Success(0),
) : RepoRepository {
    var lastOwner: String? = null
        private set

    var lastName: String? = null
        private set

    var lastStarred: Boolean? = null
        private set

    override fun observeRepositories(filter: RepoFilter): Flow<List<Repository>> = flowOf(emptyList())

    override suspend fun refreshRepositories(
        filter: RepoFilter,
        append: Boolean,
    ): NetworkResult<Int> = refreshResult

    override suspend fun getRepository(
        owner: String,
        name: String,
    ): NetworkResult<Repository> = NetworkResult.Failure(ApiError.NotFound)

    override suspend fun listBranches(
        owner: String,
        name: String,
    ): NetworkResult<List<Branch>> = NetworkResult.Success(emptyList())

    override suspend fun setStarred(
        owner: String,
        name: String,
        starred: Boolean,
    ): NetworkResult<Unit> {
        lastOwner = owner
        lastName = name
        lastStarred = starred
        return NetworkResult.Success(Unit)
    }

    override suspend fun clearCache() = Unit
}
