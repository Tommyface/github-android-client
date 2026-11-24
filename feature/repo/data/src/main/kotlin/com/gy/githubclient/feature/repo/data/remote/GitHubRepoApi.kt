/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data.remote

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.data.network.GitHubJson
import com.gy.githubclient.core.domain.network.Conditional
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.domain.model.Branch
import com.gy.githubclient.feature.repo.domain.model.CommitDetail
import com.gy.githubclient.feature.repo.domain.model.CommitSummary
import com.gy.githubclient.feature.repo.domain.model.FileContent
import com.gy.githubclient.feature.repo.domain.model.FileEntry
import com.gy.githubclient.feature.repo.domain.model.IssueComment
import com.gy.githubclient.feature.repo.domain.model.IssueItem
import com.gy.githubclient.feature.repo.domain.model.Readme
import com.gy.githubclient.feature.repo.domain.model.RepoFilter
import com.gy.githubclient.feature.repo.domain.model.Repository
import com.gy.githubclient.feature.repo.domain.model.StarHistory
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.encodeURLPath
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** 仓库列表响应：数据 + 本次响应 ETag，供缓存层持久化以供下次条件请求。 */
data class RepoListPayload(
    val items: List<Repository>,
    val etag: String?,
)

/**
 * 仓库 REST API，仅负责把统一网络层的响应解析为领域模型。
 * 所有认证、限流退避、ETag 头均由 [GitHubApiClient] 处理。
 */
class GitHubRepoApi(
    private val client: GitHubApiClient,
) {
    /** 拉取仓库列表；返回新数据或 304 未修改。 */
    suspend fun listRepositories(
        filter: RepoFilter,
        etag: String?,
    ): NetworkResult<Conditional<RepoListPayload>> =
        client.requestConditional(
            path = listPath(filter),
            etag = etag,
            mapper = { response: HttpResponse ->
                RepoListPayload(
                    items =
                        GitHubJson
                            .decode(ListSerializer(RepositoryDto.serializer()), response.bodyAsText())
                            .map { it.toDomain() },
                    etag = response.headers[HttpHeaders.ETag],
                )
            },
        )

    /** 拉取单个仓库详情。 */
    suspend fun getRepository(
        owner: String,
        name: String,
    ): NetworkResult<Repository> =
        client.request(
            path = "/repos/$owner/$name",
            mapper = { response: HttpResponse ->
                GitHubJson.decode(RepositoryDto.serializer(), response.bodyAsText()).toDomain()
            },
        )

    /** 列出仓库分支。 */
    suspend fun listBranches(
        owner: String,
        name: String,
    ): NetworkResult<List<Branch>> =
        client.request(
            path = "/repos/$owner/$name/branches?per_page=$BRANCHES_PER_PAGE",
            mapper = { response: HttpResponse ->
                GitHubJson
                    .decode(ListSerializer(BranchDto.serializer()), response.bodyAsText())
                    .map { it.toDomain() }
            },
        )

    /** star / unstar：204 无响应体，仅表达成功与否。 */
    suspend fun setStarred(
        owner: String,
        name: String,
        starred: Boolean,
    ): NetworkResult<Unit> =
        client.request(
            method = if (starred) HttpMethod.Put else HttpMethod.Delete,
            path = "/user/starred/$owner/$name",
            mapper = { _: HttpResponse -> Unit },
        )

    /**
     * 查询当前用户是否已星标该仓库。
     * GitHub 返回 204 = 已星标；404 = 未星标（由仓库层翻译为 false，不算错误）。
     */
    suspend fun starStatus(
        owner: String,
        name: String,
    ): NetworkResult<Boolean> =
        client.request(
            path = "/user/starred/$owner/$name",
            mapper = { _: HttpResponse -> true },
        )

    /**
     * Fork 到当前用户账号（user token 身份，无 App 双向安装限制）。
     * GitHub 返回 202 + fork 后的仓库对象，复制为异步进行。
     */
    suspend fun fork(
        owner: String,
        name: String,
    ): NetworkResult<Repository> =
        client.request(
            method = HttpMethod.Post,
            path = "/repos/$owner/$name/forks",
            mapper = { response: HttpResponse ->
                GitHubJson.decode(RepositoryDto.serializer(), response.bodyAsText()).toDomain()
            },
        )

    /**
     * 获取仓库 README（v0.6.0 M6）：GitHub 返回 base64 编码内容，此处解码为原文。
     * 非 base64 编码或解码失败返回 null（由仓库层以 Success(null) 表达）。
     */
    suspend fun getReadme(
        owner: String,
        name: String,
    ): NetworkResult<Readme?> =
        client.request(
            path = "/repos/$owner/$name/readme",
            mapper = { response: HttpResponse ->
                GitHubJson.decode(ReadmeDto.serializer(), response.bodyAsText()).toDomain()
            },
        )

    /**
     * 星标历史（v0.7.0 M10）：GET /repos/{owner}/{repo}/stargazers/history。
     * 无需身份、按周聚合、最新周在前；每页至多 30 周，取最近一页绘制趋势。
     * 端点可能返回 404（极新仓库无历史），由仓库层翻译为空趋势而非错误。
     */
    suspend fun getStarHistory(
        owner: String,
        name: String,
    ): NetworkResult<StarHistory> =
        client.request(
            path = "/repos/$owner/$name/stargazers/history?per_page=$STAR_HISTORY_WEEKS",
            mapper = { response: HttpResponse ->
                GitHubJson
                    .decode(ListSerializer(StarHistoryWeekDto.serializer()), response.bodyAsText())
                    .toStarHistory()
            },
        )

    /**
     * Issue / PR 只读列表（v0.7.0 M11）：GET /repos/{o}/{r}/issues?state=all。
     * GitHub 该端点同时返回 Issue 与 PR，由 DTO 依 pull_request 字段区分。
     */
    suspend fun listIssues(
        owner: String,
        name: String,
        state: String = "all",
    ): NetworkResult<List<IssueItem>> =
        client.request(
            path = "/repos/$owner/$name/issues?state=$state&per_page=$ISSUES_PER_PAGE",
            mapper = { response: HttpResponse ->
                GitHubJson
                    .decode(ListSerializer(IssueItemDto.serializer()), response.bodyAsText())
                    .map { it.toDomain() }
            },
        )

    /** Issue / PR 详情（v0.7.0 M11）：正文。 */
    suspend fun getIssueDetail(
        owner: String,
        name: String,
        number: Int,
    ): NetworkResult<IssueDetailDto> =
        client.request(
            path = "/repos/$owner/$name/issues/$number",
            mapper = { response: HttpResponse ->
                GitHubJson.decode(IssueDetailDto.serializer(), response.bodyAsText())
            },
        )

    /**
     * 目录列表（v0.9.0 M17）：GET /repos/{o}/{r}/contents/{path}?ref={branch}。
     * GitHub 对目录返回数组、对文件返回对象；本方法仅用于目录，路径Decode由调用方保证。
     */
    suspend fun listContents(
        owner: String,
        name: String,
        path: String,
        ref: String,
    ): NetworkResult<List<FileEntry>> =
        client.request(
            path = contentsPath(owner, name, path, ref),
            mapper = { response: HttpResponse ->
                GitHubJson
                    .decode(ListSerializer(ContentEntryDto.serializer()), response.bodyAsText())
                    .map { it.toFileEntry() }
                    .sortedWith(compareBy({ it.type != "dir" }, { it.name.lowercase() }))
            },
        )

    /** 单文件内容（v0.9.0 M17）：同一端点返回对象，解析 base64 content。 */
    suspend fun getFileContent(
        owner: String,
        name: String,
        path: String,
        ref: String,
    ): NetworkResult<FileContent> =
        client.request(
            path = contentsPath(owner, name, path, ref),
            mapper = { response: HttpResponse ->
                GitHubJson.decode(ContentEntryDto.serializer(), response.bodyAsText()).toFileContent()
            },
        )

    /** 提交历史（v0.12.0 N10）：GET /repos/{o}/{r}/commits?page=&per_page=。 */
    suspend fun listCommits(
        owner: String,
        name: String,
        page: Int,
        perPage: Int,
    ): NetworkResult<List<CommitSummary>> =
        client.request(
            path = "/repos/$owner/$name/commits?page=$page&per_page=$perPage",
            mapper = { response: HttpResponse ->
                GitHubJson
                    .decode(ListSerializer(CommitItemDto.serializer()), response.bodyAsText())
                    .map { it.toSummary() }
            },
        )

    /** 单次提交详情（v0.12.0 N10）：GET /repos/{o}/{r}/commits/{sha}。 */
    suspend fun getCommitDetail(
        owner: String,
        name: String,
        sha: String,
    ): NetworkResult<CommitDetail> =
        client.request(
            path = "/repos/$owner/$name/commits/${sha.encodeURLPath()}",
            mapper = { response: HttpResponse ->
                GitHubJson.decode(CommitDetailDto.serializer(), response.bodyAsText()).toDetail()
            },
        )

    /** CI check runs（v0.13.0 A2）：GET /repos/{o}/{r}/commits/{sha}/check-runs。 */
    suspend fun listCheckRuns(
        owner: String,
        name: String,
        sha: String,
    ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.CheckRun>> =
        client.request(
            path = "/repos/$owner/$name/commits/${sha.encodeURLPath()}/check-runs?per_page=$CHECKS_PER_PAGE",
            mapper = { response: HttpResponse ->
                GitHubJson
                    .decode(CheckRunsResponseDto.serializer(), response.bodyAsText())
                    .toDomainRuns()
            },
        )

    /** Actions workflow run 列表（v0.14.0 A3）：GET /repos/{o}/{r}/actions/runs?page=&per_page=。 */
    suspend fun listWorkflowRuns(
        owner: String,
        name: String,
        page: Int,
        perPage: Int,
    ): NetworkResult<com.gy.githubclient.feature.repo.domain.model.WorkflowRunPage> =
        client.request(
            path = "/repos/$owner/$name/actions/runs?page=$page&per_page=$perPage",
            mapper = { response: HttpResponse ->
                GitHubJson
                    .decode(WorkflowRunsResponseDto.serializer(), response.bodyAsText())
                    .toPage(perPage)
            },
        )

    /** 某文件的提交历史（v0.14.0 A4 降级）：GET /repos/{o}/{r}/commits?path=&ref=。 */
    suspend fun listFileCommits(
        owner: String,
        name: String,
        path: String,
        ref: String,
    ): NetworkResult<List<CommitSummary>> =
        client.request(
            path = "/repos/$owner/$name/commits?path=${path.encodeURLPath()}&ref=${ref.encodeURLPath()}&per_page=$FILE_COMMITS_PER_PAGE",
            mapper = { response: HttpResponse ->
                GitHubJson
                    .decode(ListSerializer(CommitItemDto.serializer()), response.bodyAsText())
                    .map { it.toSummary() }
            },
        )

    /** 递归文件树（v0.16.0 P2-a）：GET /repos/{o}/{r}/git/trees/{ref}?recursive=1。 */
    suspend fun getTree(
        owner: String,
        name: String,
        ref: String,
    ): NetworkResult<com.gy.githubclient.feature.repo.domain.model.RepoTree> =
        client.request(
            path = "/repos/$owner/$name/git/trees/${ref.encodeURLPath()}?recursive=1",
            mapper = { response: HttpResponse ->
                GitHubJson.decode(GitTreeResponseDto.serializer(), response.bodyAsText()).toDomain()
            },
        )

    /** 语言构成（v0.16.0 P2-b）：GET /repos/{o}/{r}/languages，返回 Map<语言, 字节数>。 */
    suspend fun getLanguages(
        owner: String,
        name: String,
    ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.LanguageShare>> =
        client.request(
            path = "/repos/$owner/$name/languages",
            mapper = { response: HttpResponse ->
                val bytes =
                    GitHubJson.decode(
                        MapSerializer(String.serializer(), Long.serializer()),
                        response.bodyAsText(),
                    )
                bytes.toLanguageShares()
            },
        )

    /** 贡献者（v0.16.0 P2-c）：GET /repos/{o}/{r}/contributors。 */
    suspend fun listContributors(
        owner: String,
        name: String,
    ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.Contributor>> =
        client.request(
            path = "/repos/$owner/$name/contributors?per_page=$CONTRIBUTORS_PER_PAGE",
            mapper = { response: HttpResponse ->
                GitHubJson
                    .decode(ListSerializer(ContributorDto.serializer()), response.bodyAsText())
                    .map { it.toDomain() }
            },
        )

    /** combined commit status（v0.13.0 A2）：GET /repos/{o}/{r}/commits/{sha}/status。 */
    suspend fun getCombinedStatus(
        owner: String,
        name: String,
        sha: String,
    ): NetworkResult<List<com.gy.githubclient.feature.repo.domain.model.CommitStatus>> =
        client.request(
            path = "/repos/$owner/$name/commits/${sha.encodeURLPath()}/status",
            mapper = { response: HttpResponse ->
                GitHubJson
                    .decode(CombinedStatusDto.serializer(), response.bodyAsText())
                    .toDomainStatuses()
            },
        )

    /** Issue / PR 评论时间线（v0.7.0 M11）。 */
    suspend fun listIssueComments(
        owner: String,
        name: String,
        number: Int,
    ): NetworkResult<List<IssueComment>> =
        client.request(
            path = "/repos/$owner/$name/issues/$number/comments?per_page=$ISSUES_PER_PAGE",
            mapper = { response: HttpResponse ->
                GitHubJson
                    .decode(ListSerializer(IssueCommentDto.serializer()), response.bodyAsText())
                    .map { it.toDomain() }
            },
        )

    /**
     * contents 端点路径：owner/name 与路径各分段分别 URL 编码（保留 / 分隔符）。
     * 空路径表示仓库根目录。ref 为分支/tag/sha。
     */
    private fun contentsPath(
        owner: String,
        name: String,
        path: String,
        ref: String,
    ): String {
        val encodedPath =
            path
                .split('/')
                .filter { it.isNotBlank() }
                .joinToString("/") { it.encodeURLPath() }
        val base = "/repos/${owner.encodeURLPath()}/${name.encodeURLPath()}/contents"
        val withPath = if (encodedPath.isBlank()) base else "$base/$encodedPath"
        return "$withPath?ref=${ref.encodeURLPath()}"
    }

    private fun listPath(filter: RepoFilter): String {
        val type = filter.type.name.lowercase()
        val sort = filter.sort.name.lowercase()
        val direction = filter.direction.name.lowercase()
        return "/user/repos?type=$type&sort=$sort&direction=$direction" +
            "&page=${filter.page}&per_page=${filter.perPage}"
    }

    private companion object {
        const val BRANCHES_PER_PAGE = 100
        const val STAR_HISTORY_WEEKS = 30
        const val ISSUES_PER_PAGE = 30
        const val CHECKS_PER_PAGE = 30
        const val FILE_COMMITS_PER_PAGE = 30
        const val CONTRIBUTORS_PER_PAGE = 100
    }
}
