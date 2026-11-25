/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain.repository

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.core.mutation.MutationResult
import com.gy.githubclient.feature.repo.domain.model.Branch
import com.gy.githubclient.feature.repo.domain.model.CheckOverview
import com.gy.githubclient.feature.repo.domain.model.CommitDetail
import com.gy.githubclient.feature.repo.domain.model.CommitSummary
import com.gy.githubclient.feature.repo.domain.model.Contributor
import com.gy.githubclient.feature.repo.domain.model.FileContent
import com.gy.githubclient.feature.repo.domain.model.FileEntry
import com.gy.githubclient.feature.repo.domain.model.IssueDetail
import com.gy.githubclient.feature.repo.domain.model.IssueItem
import com.gy.githubclient.feature.repo.domain.model.LanguageShare
import com.gy.githubclient.feature.repo.domain.model.Readme
import com.gy.githubclient.feature.repo.domain.model.RepoFilter
import com.gy.githubclient.feature.repo.domain.model.RepoTree
import com.gy.githubclient.feature.repo.domain.model.Repository
import com.gy.githubclient.feature.repo.domain.model.StarHistory
import com.gy.githubclient.feature.repo.domain.model.WorkflowRunPage
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

    /** star / unstar；走写操作信封，返回细粒度错误。 */
    suspend fun setStarred(
        owner: String,
        name: String,
        starred: Boolean,
    ): MutationResult<Unit>

    /** 查询当前用户是否 watch（订阅）了该仓库；404 视为未订阅。 */
    suspend fun isWatched(
        owner: String,
        name: String,
    ): NetworkResult<Boolean>

    /** watch / unwatch 仓库；写入成功才更新本地状态。 */
    suspend fun setWatched(
        owner: String,
        name: String,
        watched: Boolean,
    ): MutationResult<Unit>

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

    /**
     * 获取仓库 README（v0.6.0 M6）。
     * 仓库无 README 时返回 Success(null)（404 不视为错误）；解码失败同理。
     */
    suspend fun getReadme(
        owner: String,
        name: String,
    ): NetworkResult<Readme?>

    /** 获取星标趋势（v0.7.0 M10）：按周累计曲线；无历史返回空 StarHistory。 */
    suspend fun getStarHistory(
        owner: String,
        name: String,
    ): NetworkResult<StarHistory>

    /** Issue / PR 只读列表（v0.7.0 M11）。 */
    suspend fun listIssues(
        owner: String,
        name: String,
        state: String = "all",
    ): NetworkResult<List<IssueItem>>

    /** Issue / PR 详情（含评论时间线，v0.7.0 M11）。 */
    suspend fun getIssueDetail(
        owner: String,
        name: String,
        number: Int,
    ): NetworkResult<IssueDetail>

    /** 列出仓库目录内容（v0.9.0 M17）；path 为空表示根目录。 */
    suspend fun listContents(
        owner: String,
        name: String,
        path: String = "",
        ref: String,
    ): NetworkResult<List<FileEntry>>

    /** 读取仓库单文件文本（v0.9.0 M17）；过大/二进制时 content 为空并标记。 */
    suspend fun getFileContent(
        owner: String,
        name: String,
        path: String,
        ref: String,
    ): NetworkResult<FileContent>

    /** 提交历史（v0.12.0 N10）：page 从 1 开始。 */
    suspend fun listCommits(
        owner: String,
        name: String,
        page: Int = 1,
        perPage: Int = 30,
    ): NetworkResult<List<CommitSummary>>

    /** 单次提交详情（含变更文件 patch，v0.12.0 N10）。 */
    suspend fun getCommitDetail(
        owner: String,
        name: String,
        sha: String,
    ): NetworkResult<CommitDetail>

    /**
     * 某次提交的 CI 检查概览（v0.13.0 A2）：check-runs + statuses 聚合。
     * 两个端点任一失败时以空列表降级，不阻塞详情展示。
     */
    suspend fun getCheckOverview(
        owner: String,
        name: String,
        sha: String,
    ): NetworkResult<CheckOverview>

    /** Actions workflow run 列表（v0.14.0 A3）：page 从 1 开始，只读。 */
    suspend fun listWorkflowRuns(
        owner: String,
        name: String,
        page: Int = 1,
        perPage: Int = 30,
    ): NetworkResult<WorkflowRunPage>

    /** 某文件的提交历史（v0.14.0 A4 降级）：REST commits?path=，只读。 */
    suspend fun listFileCommits(
        owner: String,
        name: String,
        path: String,
        ref: String,
    ): NetworkResult<List<CommitSummary>>

    /** 递归文件树（v0.16.0 P2-a）：GET /git/trees/{ref}?recursive=1，只读。 */
    suspend fun getTree(
        owner: String,
        name: String,
        ref: String,
    ): NetworkResult<RepoTree>

    /** 语言构成（v0.16.0 P2-b）：GET /languages 字节占比，只读。 */
    suspend fun getLanguages(
        owner: String,
        name: String,
    ): NetworkResult<List<LanguageShare>>

    /** 贡献者（v0.16.0 P2-c）：GET /contributors，只读。 */
    suspend fun listContributors(
        owner: String,
        name: String,
    ): NetworkResult<List<Contributor>>

    /** 仓库标签（v0.17.0 P3-a）：GET /labels，只读。 */
    suspend fun listLabels(
        owner: String,
        name: String,
    ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.RepoLabel>>

    /** 里程碑（v0.17.0 P3-b）：GET /milestones?state=all，只读。 */
    suspend fun listMilestones(
        owner: String,
        name: String,
    ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.Milestone>>

    /** Fork 列表（v0.17.0 P3-c）：GET /forks，只读。 */
    suspend fun listForks(
        owner: String,
        name: String,
    ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.ForkItem>>

    /** 清空本地仓库缓存（登出时调用，防止串号）。 */
    suspend fun clearCache()
}
