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
import com.gy.githubclient.feature.repo.domain.model.StarHistory
import com.gy.githubclient.feature.repo.domain.model.StarWeekEntry
import com.gy.githubclient.feature.repo.domain.repository.RepoRepository
import com.gy.githubclient.feature.repo.domain.usecase.GetFileContentUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListContentsUseCase
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
    fun starHistoryReversesNewestFirstAndAccumulatesWeeklyDeltas() {
        // GitHub 返回「最新周在前」：week=300(+5), week=200(+2), week=100(+3)
        // 累计曲线应为时间升序：3 → 5 → 10。
        val history =
            StarHistory.fromWeekly(
                listOf(
                    StarWeekEntry(300L, 5),
                    StarWeekEntry(200L, 2),
                    StarWeekEntry(100L, 3),
                ),
            )
        assertEquals(listOf(100L, 200L, 300L), history.points.map { it.weekStartEpoch })
        assertEquals(listOf(3, 5, 10), history.points.map { it.cumulativeStars })
        assertEquals(10, history.currentTotal)
        assertEquals(5, history.peakWeekly)
    }

    @Test
    fun starHistoryTreatsNegativeDeltaAsZeroAndHandlesEmpty() {
        val history =
            StarHistory.fromWeekly(
                listOf(
                    StarWeekEntry(200L, -4),
                    StarWeekEntry(100L, 3),
                ),
            )
        // 负增量按 0，累计不倒退。
        assertEquals(listOf(3, 3), history.points.map { it.cumulativeStars })
        assertEquals(0, StarHistory.fromWeekly(emptyList()).currentTotal)
    }

    @Test
    fun contentsUseCasesDelegate() =
        runTest {
            val entries = ListContentsUseCase(StubRepoRepository())("o", "r", ref = "main")
            assertTrue(entries is NetworkResult.Success)
            assertTrue((entries as NetworkResult.Success).data.isEmpty())

            val file = GetFileContentUseCase(StubRepoRepository())("o", "r", "a.txt", "main")
            assertTrue(file is NetworkResult.Success)
            assertEquals("hi", (file as NetworkResult.Success).data.content)
        }

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

    override suspend fun getReadme(
        owner: String,
        name: String,
    ): NetworkResult<com.gy.githubclient.feature.repo.domain.model.Readme?> = NetworkResult.Success(null)

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

    override suspend fun isStarred(
        owner: String,
        name: String,
    ): NetworkResult<Boolean> = NetworkResult.Success(false)

    override suspend fun fork(
        owner: String,
        name: String,
    ): NetworkResult<Repository> = NetworkResult.Failure(ApiError.NotFound)

    override suspend fun getStarHistory(
        owner: String,
        name: String,
    ): NetworkResult<com.gy.githubclient.feature.repo.domain.model.StarHistory> =
        NetworkResult.Success(
            com.gy.githubclient.feature.repo.domain.model
                .StarHistory(emptyList()),
        )

    override suspend fun listIssues(
        owner: String,
        name: String,
        state: String,
    ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.IssueItem>> = NetworkResult.Success(emptyList())

    override suspend fun getIssueDetail(
        owner: String,
        name: String,
        number: Int,
    ): NetworkResult<com.gy.githubclient.feature.repo.domain.model.IssueDetail> =
        NetworkResult.Failure(ApiError.NotFound)

    override suspend fun listContents(
        owner: String,
        name: String,
        path: String,
        ref: String,
    ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.FileEntry>> =
        NetworkResult.Success(emptyList())

    override suspend fun getFileContent(
        owner: String,
        name: String,
        path: String,
        ref: String,
    ): NetworkResult<com.gy.githubclient.feature.repo.domain.model.FileContent> =
        NetworkResult.Success(
            com.gy.githubclient.feature.repo.domain.model
                .FileContent(path = path, name = "a.txt", content = "hi", size = 2),
        )

    override suspend fun listCommits(
        owner: String,
        name: String,
        page: Int,
        perPage: Int,
    ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.CommitSummary>> = NetworkResult.Success(emptyList())

    override suspend fun getCommitDetail(
        owner: String,
        name: String,
        sha: String,
    ): NetworkResult<com.gy.githubclient.feature.repo.domain.model.CommitDetail> =
        NetworkResult.Failure(ApiError.NotFound)

    override suspend fun getCheckOverview(
        owner: String,
        name: String,
        sha: String,
    ): NetworkResult<com.gy.githubclient.feature.repo.domain.model.CheckOverview> =
        NetworkResult.Success(
            com.gy.githubclient.feature.repo.domain.model
                .CheckOverview(sha = sha),
        )

    override suspend fun clearCache() = Unit
}
