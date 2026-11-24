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
import com.gy.githubclient.feature.repo.domain.model.RepoFilter
import com.gy.githubclient.feature.repo.domain.model.Repository
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import kotlinx.serialization.builtins.ListSerializer

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

    private fun listPath(filter: RepoFilter): String {
        val type = filter.type.name.lowercase()
        val sort = filter.sort.name.lowercase()
        val direction = filter.direction.name.lowercase()
        return "/user/repos?type=$type&sort=$sort&direction=$direction" +
            "&page=${filter.page}&per_page=${filter.perPage}"
    }

    private companion object {
        const val BRANCHES_PER_PAGE = 100
    }
}
