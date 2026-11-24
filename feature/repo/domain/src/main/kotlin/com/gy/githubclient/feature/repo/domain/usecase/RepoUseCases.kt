/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain.usecase

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.domain.model.Branch
import com.gy.githubclient.feature.repo.domain.model.CheckOverview
import com.gy.githubclient.feature.repo.domain.model.FileContent
import com.gy.githubclient.feature.repo.domain.model.FileEntry
import com.gy.githubclient.feature.repo.domain.model.IssueDetail
import com.gy.githubclient.feature.repo.domain.model.IssueItem
import com.gy.githubclient.feature.repo.domain.model.Readme
import com.gy.githubclient.feature.repo.domain.model.RepoFilter
import com.gy.githubclient.feature.repo.domain.model.Repository
import com.gy.githubclient.feature.repo.domain.model.StarHistory
import com.gy.githubclient.feature.repo.domain.model.WorkflowRunPage
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

/** 获取仓库 README（无 README 返回 null）。 */
class GetReadmeUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
    ): NetworkResult<Readme?> = repository.getReadme(owner, name)
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

/** 获取星标趋势（按周累计曲线）。 */
class GetStarHistoryUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
    ): NetworkResult<StarHistory> = repository.getStarHistory(owner, name)
}

/** Issue / PR 只读列表。 */
class ListIssuesUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
        state: String = "all",
    ): NetworkResult<List<IssueItem>> = repository.listIssues(owner, name, state)
}

/** Issue / PR 详情（含评论时间线）。 */
class GetIssueDetailUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
        number: Int,
    ): NetworkResult<IssueDetail> = repository.getIssueDetail(owner, name, number)
}

/** 列出仓库目录内容（v0.9.0 M17）。 */
class ListContentsUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
        path: String = "",
        ref: String,
    ): NetworkResult<List<FileEntry>> = repository.listContents(owner, name, path, ref)
}

/** 读取仓库单文件文本（v0.9.0 M17）。 */
class GetFileContentUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
        path: String,
        ref: String,
    ): NetworkResult<FileContent> = repository.getFileContent(owner, name, path, ref)
}

/** 获取某次提交的 CI 检查概览（v0.13.0 A2）。 */
class GetCheckOverviewUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
        sha: String,
    ): NetworkResult<CheckOverview> = repository.getCheckOverview(owner, name, sha)
}

/** 获取 Actions workflow run 分页列表（v0.14.0 A3，只读）。 */
class ListWorkflowRunsUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
        page: Int = 1,
        perPage: Int = 30,
    ): NetworkResult<WorkflowRunPage> = repository.listWorkflowRuns(owner, name, page, perPage)
}

/** 获取某文件的提交历史（v0.14.0 A4 降级，只读）。 */
class ListFileCommitsUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
        path: String,
        ref: String,
    ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.CommitSummary>> =
        repository.listFileCommits(owner, name, path, ref)
}

/** 清空缓存（登出时）。 */
class ClearRepoCacheUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke() = repository.clearCache()
}
