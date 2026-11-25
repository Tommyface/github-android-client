/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain

import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.core.mutation.MutationResult
import com.gy.githubclient.feature.repo.domain.model.Branch
import com.gy.githubclient.feature.repo.domain.model.CheckOverview
import com.gy.githubclient.feature.repo.domain.model.CommitDetail
import com.gy.githubclient.feature.repo.domain.model.CommitSummary
import com.gy.githubclient.feature.repo.domain.model.DiffLineKind
import com.gy.githubclient.feature.repo.domain.model.FileContent
import com.gy.githubclient.feature.repo.domain.model.FileEntry
import com.gy.githubclient.feature.repo.domain.model.IssueDetail
import com.gy.githubclient.feature.repo.domain.model.IssueItem
import com.gy.githubclient.feature.repo.domain.model.Readme
import com.gy.githubclient.feature.repo.domain.model.RepoFilter
import com.gy.githubclient.feature.repo.domain.model.Repository
import com.gy.githubclient.feature.repo.domain.model.StarHistory
import com.gy.githubclient.feature.repo.domain.model.parseDiffPatch
import com.gy.githubclient.feature.repo.domain.repository.RepoRepository
import com.gy.githubclient.feature.repo.domain.usecase.GetCommitDetailUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListCommitsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.12.0 N10 回归：提交模型派生属性、diff 解析与用例委托。 */
class CommitDomainTest {
    private val repo =
        object : RepoRepository {
            override fun observeRepositories(filter: RepoFilter): Flow<List<Repository>> = flowOf(emptyList())

            override suspend fun refreshRepositories(
                filter: RepoFilter,
                append: Boolean,
            ): NetworkResult<Int> = NetworkResult.Success(0)

            override suspend fun getRepository(owner: String, name: String): NetworkResult<Repository> =
                NetworkResult.Failure(ApiError.NotFound)

            override suspend fun listBranches(owner: String, name: String): NetworkResult<List<Branch>> =
                NetworkResult.Success(emptyList())

            override suspend fun setStarred(
                owner: String,
                name: String,
                starred: Boolean,
            ): MutationResult<Unit> = MutationResult.Success(Unit)

            override suspend fun isWatched(owner: String, name: String): NetworkResult<Boolean> = NetworkResult.Success(false)

            override suspend fun setWatched(
                owner: String,
                name: String,
                watched: Boolean,
            ): MutationResult<Unit> = MutationResult.Success(Unit)

            override suspend fun isStarred(owner: String, name: String): NetworkResult<Boolean> = NetworkResult.Success(false)

            override suspend fun fork(owner: String, name: String): NetworkResult<Repository> = NetworkResult.Failure(ApiError.NotFound)

            override suspend fun getReadme(owner: String, name: String): NetworkResult<Readme?> = NetworkResult.Success(null)

            override suspend fun getStarHistory(owner: String, name: String): NetworkResult<StarHistory> =
                NetworkResult.Success(StarHistory(emptyList()))

            override suspend fun listIssues(
                owner: String,
                name: String,
                state: String,
            ): NetworkResult<List<IssueItem>> = NetworkResult.Success(emptyList())

            override suspend fun getIssueDetail(
                owner: String,
                name: String,
                number: Int,
            ): NetworkResult<IssueDetail> = NetworkResult.Failure(ApiError.NotFound)

            override suspend fun listContents(
                owner: String,
                name: String,
                path: String,
                ref: String,
            ): NetworkResult<List<FileEntry>> = NetworkResult.Success(emptyList())

            override suspend fun getFileContent(
                owner: String,
                name: String,
                path: String,
                ref: String,
            ): NetworkResult<FileContent> = NetworkResult.Success(FileContent(path, name, "x", 1))

            override suspend fun listCommits(
                owner: String,
                name: String,
                page: Int,
                perPage: Int,
            ): NetworkResult<List<CommitSummary>> =
                NetworkResult.Success(
                    listOf(
                        CommitSummary(
                            "abcdef1234567890",
                            "修复登录超时\n\n详细描述",
                            "dev",
                            "2026-11-25T00:00:00Z",
                            "https://github.com/o/r/commit/abcdef1234567890",
                        ),
                    ),
                )

            override suspend fun getCommitDetail(
                owner: String,
                name: String,
                sha: String,
            ): NetworkResult<CommitDetail> = NetworkResult.Failure(ApiError.NotFound)

            override suspend fun getCheckOverview(
                owner: String,
                name: String,
                sha: String,
            ): NetworkResult<CheckOverview> = NetworkResult.Success(CheckOverview(sha = sha))

            override suspend fun listWorkflowRuns(
                owner: String,
                name: String,
                page: Int,
                perPage: Int,
            ): NetworkResult<com.gy.githubclient.feature.repo.domain.model.WorkflowRunPage> =
                NetworkResult.Success(
                    com.gy.githubclient.feature.repo.domain.model
                        .WorkflowRunPage(emptyList(), false),
                )

            override suspend fun listFileCommits(
                owner: String,
                name: String,
                path: String,
                ref: String,
            ): NetworkResult<List<CommitSummary>> = NetworkResult.Success(emptyList())

            override suspend fun getTree(
                owner: String,
                name: String,
                ref: String,
            ): NetworkResult<com.gy.githubclient.feature.repo.domain.model.RepoTree> =
                NetworkResult.Success(
                    com.gy.githubclient.feature.repo.domain.model
                        .RepoTree(emptyList()),
                )

            override suspend fun getLanguages(
                owner: String,
                name: String,
            ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.LanguageShare>> = NetworkResult.Success(emptyList())

            override suspend fun listContributors(
                owner: String,
                name: String,
            ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.Contributor>> = NetworkResult.Success(emptyList())

            override suspend fun listLabels(
                owner: String,
                name: String,
            ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.RepoLabel>> = NetworkResult.Success(emptyList())

            override suspend fun listMilestones(
                owner: String,
                name: String,
            ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.Milestone>> = NetworkResult.Success(emptyList())

            override suspend fun listForks(
                owner: String,
                name: String,
            ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.ForkItem>> = NetworkResult.Success(emptyList())

            override suspend fun clearCache() = Unit
        }

    @Test
    fun commitSummaryDerivedFields() {
        val c = CommitSummary("abcdef1234567890", "修复登录超时\n\n详细描述", null, null, "")
        assertEquals("abcdef1", c.shortSha)
        assertEquals("修复登录超时", c.title)
        val short = CommitSummary("abc", "", null, null, "")
        assertEquals("abc", short.shortSha)
        assertEquals("abc", short.title)
    }

    @Test
    fun parsesDiffPatchIntoKinds() {
        val patch =
            """
            @@ -1,3 +1,4 @@
             context line
            -removed
            +added
            + added two
            \ No newline at end of file
            """.trimIndent()
        val lines = parseDiffPatch(patch)
        assertEquals(DiffLineKind.HEADER, lines[0].kind)
        assertEquals(DiffLineKind.CONTEXT, lines[1].kind)
        assertEquals(DiffLineKind.DELETE, lines[2].kind)
        assertEquals(2, lines.count { it.kind == DiffLineKind.ADD })
        assertTrue(lines.last().text.contains("No newline"))
        assertEquals(patch, lines.joinToString("\n") { it.text })
    }

    @Test
    fun emptyPatchYieldsEmptyLines() = assertTrue(parseDiffPatch("").isEmpty())

    @Test
    fun listCommitsUseCaseDelegates() =
        runTest {
            val result = ListCommitsUseCase(repo)("o", "r", 1)
            assertTrue(result is NetworkResult.Success)
            assertEquals("修复登录超时", (result as NetworkResult.Success).data.first().title)
        }

    @Test
    fun getCommitDetailUseCaseDelegatesFailure() =
        runTest {
            val result = GetCommitDetailUseCase(repo)("o", "r", "sha")
            assertTrue(result is NetworkResult.Failure)
        }
}
