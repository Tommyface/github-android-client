/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data

import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.Conditional
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.data.local.RepoDao
import com.gy.githubclient.feature.repo.data.local.toDomain
import com.gy.githubclient.feature.repo.data.local.toEntity
import com.gy.githubclient.feature.repo.data.remote.GitHubRepoApi
import com.gy.githubclient.feature.repo.data.remote.buildCheckOverview
import com.gy.githubclient.feature.repo.domain.model.Branch
import com.gy.githubclient.feature.repo.domain.model.CheckOverview
import com.gy.githubclient.feature.repo.domain.model.CommitDetail
import com.gy.githubclient.feature.repo.domain.model.CommitSummary
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
import kotlinx.coroutines.flow.map
import com.gy.githubclient.feature.repo.data.remote.toDomain as toDomainDto

/**
 * 仓库仓库实现（Room 为单一数据源）：
 * - UI 只观察 [observeRepositories] 的缓存 Flow。
 * - [refreshRepositories] 走 ETag 条件请求；304 未修改则不动缓存，返回 0。
 * - 网络失败时保留旧缓存，由上层决定是否提示；离线仍可浏览。
 */
class RepoRepositoryImpl(
    private val dao: RepoDao,
    private val api: GitHubRepoApi,
) : RepoRepository {
    override fun observeRepositories(filter: RepoFilter): Flow<List<Repository>> =
        dao.observeByBucket(bucketOf(filter)).map { entities -> entities.map { it.toDomain() } }

    override suspend fun refreshRepositories(
        filter: RepoFilter,
        append: Boolean,
    ): NetworkResult<Int> {
        val bucket = bucketOf(filter)
        val etag = if (append) null else dao.getEtag(bucket)
        return when (val result = api.listRepositories(filter, etag)) {
            is NetworkResult.Failure -> NetworkResult.Failure(result.error)

            is NetworkResult.Success -> {
                when (val conditional = result.data) {
                    Conditional.NotModified -> NetworkResult.Success(0)

                    is Conditional.Data -> {
                        val payload = conditional.value
                        val entities = payload.items.map { it.toEntity(bucket, filter.page) }
                        if (append) {
                            dao.appendAll(entities)
                        } else {
                            dao.replaceBucket(bucket, entities, etag = payload.etag)
                        }
                        NetworkResult.Success(payload.items.size)
                    }
                }
            }
        }
    }

    override suspend fun getRepository(
        owner: String,
        name: String,
    ): NetworkResult<Repository> =
        when (val result = api.getRepository(owner, name)) {
            is NetworkResult.Success -> {
                // 详情响应不含当前用户的 star 状态，需单独查询置位（失败不阻塞详情展示）。
                val starred =
                    when (val starredResult = isStarred(owner, name)) {
                        is NetworkResult.Success -> starredResult.data
                        is NetworkResult.Failure -> false
                    }
                NetworkResult.Success(result.data.copy(isStarred = starred))
            }

            is NetworkResult.Failure -> NetworkResult.Failure(result.error)
        }

    override suspend fun listBranches(
        owner: String,
        name: String,
    ): NetworkResult<List<Branch>> = api.listBranches(owner, name)

    override suspend fun setStarred(
        owner: String,
        name: String,
        starred: Boolean,
    ): NetworkResult<Unit> =
        when (val result = api.setStarred(owner, name, starred)) {
            is NetworkResult.Success -> {
                dao.findByFullName(fullName = "$owner/$name")?.let { entity ->
                    dao.updateStarred(id = entity.id, starred = starred)
                }
                NetworkResult.Success(Unit)
            }

            is NetworkResult.Failure -> NetworkResult.Failure(result.error)
        }

    override suspend fun isStarred(
        owner: String,
        name: String,
    ): NetworkResult<Boolean> =
        when (val result = api.starStatus(owner, name)) {
            is NetworkResult.Success -> NetworkResult.Success(true)
            is NetworkResult.Failure ->
                // 404 = 未星标，属正常态而非错误；其余错误如实上抛。
                if (result.error is ApiError.NotFound) {
                    NetworkResult.Success(false)
                } else {
                    NetworkResult.Failure(result.error)
                }
        }

    override suspend fun fork(
        owner: String,
        name: String,
    ): NetworkResult<Repository> = api.fork(owner, name)

    override suspend fun getReadme(
        owner: String,
        name: String,
    ): NetworkResult<Readme?> =
        when (val result = api.getReadme(owner, name)) {
            is NetworkResult.Success -> NetworkResult.Success(result.data)
            is NetworkResult.Failure ->
                // 无 README 是常态（404）而非错误；解码失败同样以 null 表达。
                if (result.error is ApiError.NotFound) {
                    NetworkResult.Success(null)
                } else {
                    NetworkResult.Failure(result.error)
                }
        }

    override suspend fun getStarHistory(
        owner: String,
        name: String,
    ): NetworkResult<StarHistory> =
        when (val result = api.getStarHistory(owner, name)) {
            is NetworkResult.Success -> result
            // 无历史（404）是极新仓库的常见态，以空趋势表达而非错误。
            is NetworkResult.Failure ->
                if (result.error is ApiError.NotFound) {
                    NetworkResult.Success(StarHistory(emptyList()))
                } else {
                    result
                }
        }

    override suspend fun listIssues(
        owner: String,
        name: String,
        state: String,
    ): NetworkResult<List<IssueItem>> = api.listIssues(owner, name, state)

    override suspend fun getIssueDetail(
        owner: String,
        name: String,
        number: Int,
    ): NetworkResult<IssueDetail> =
        when (val detailResult = api.getIssueDetail(owner, name, number)) {
            is NetworkResult.Failure -> NetworkResult.Failure(detailResult.error)
            is NetworkResult.Success -> {
                // 评论失败不阻塞正文展示：以空时间线降级。
                val comments =
                    when (val commentsResult = api.listIssueComments(owner, name, number)) {
                        is NetworkResult.Success -> commentsResult.data
                        is NetworkResult.Failure -> emptyList()
                    }
                NetworkResult.Success(detailResult.data.toDomainDto(comments))
            }
        }

    override suspend fun listContents(
        owner: String,
        name: String,
        path: String,
        ref: String,
    ): NetworkResult<List<FileEntry>> = api.listContents(owner, name, path, ref)

    override suspend fun getFileContent(
        owner: String,
        name: String,
        path: String,
        ref: String,
    ): NetworkResult<FileContent> = api.getFileContent(owner, name, path, ref)

    override suspend fun listCommits(
        owner: String,
        name: String,
        page: Int,
        perPage: Int,
    ): NetworkResult<List<CommitSummary>> = api.listCommits(owner, name, page, perPage)

    override suspend fun getCommitDetail(
        owner: String,
        name: String,
        sha: String,
    ): NetworkResult<CommitDetail> = api.getCommitDetail(owner, name, sha)

    override suspend fun getCheckOverview(
        owner: String,
        name: String,
        sha: String,
    ): NetworkResult<CheckOverview> {
        // 两个端点独立加载：任一失败以空列表降级，不阻塞 CI 概览其它部分。
        val runs =
            when (val result = api.listCheckRuns(owner, name, sha)) {
                is NetworkResult.Success -> result.data
                is NetworkResult.Failure -> emptyList()
            }
        val statuses =
            when (val result = api.getCombinedStatus(owner, name, sha)) {
                is NetworkResult.Success -> result.data
                is NetworkResult.Failure -> emptyList()
            }
        return NetworkResult.Success(buildCheckOverview(sha, runs, statuses))
    }

    override suspend fun listWorkflowRuns(
        owner: String,
        name: String,
        page: Int,
        perPage: Int,
    ): NetworkResult<WorkflowRunPage> = api.listWorkflowRuns(owner, name, page, perPage)

    override suspend fun listFileCommits(
        owner: String,
        name: String,
        path: String,
        ref: String,
    ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.CommitSummary>> =
        api.listFileCommits(owner, name, path, ref)

    override suspend fun getTree(
        owner: String,
        name: String,
        ref: String,
    ): NetworkResult<com.gy.githubclient.feature.repo.domain.model.RepoTree> = api.getTree(owner, name, ref)

    override suspend fun getLanguages(
        owner: String,
        name: String,
    ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.LanguageShare>> =
        api.getLanguages(owner, name)

    override suspend fun listContributors(
        owner: String,
        name: String,
    ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.Contributor>> =
        api.listContributors(owner, name)

    override suspend fun listLabels(
        owner: String,
        name: String,
    ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.RepoLabel>> = api.listLabels(owner, name)

    override suspend fun listMilestones(
        owner: String,
        name: String,
    ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.Milestone>> = api.listMilestones(owner, name)

    override suspend fun listForks(
        owner: String,
        name: String,
    ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.ForkItem>> = api.listForks(owner, name)

    override suspend fun clearCache() {
        dao.clearAll()
        dao.clearAllMeta()
    }

    /** bucket 键由归属维度 + 排序 + 方向组成，避免不同筛选条件串缓存。 */
    private fun bucketOf(filter: RepoFilter): String =
        "${filter.type.name}_${filter.sort.name}_${filter.direction.name}"
}
