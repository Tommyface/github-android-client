/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.data.remote

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.data.network.GitHubJson
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.discover.domain.model.ReleaseInfo
import com.gy.githubclient.feature.discover.domain.model.TagInfo
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.encodeURLPath
import kotlinx.serialization.builtins.ListSerializer

/**
 * Release REST API：仅解析统一网络层返回的响应。
 * 认证、限流退避、错误映射全部由 [GitHubApiClient] 处理。
 */
class GitHubReleaseApi(
    private val client: GitHubApiClient,
) {
    /** 拉取某仓库最新稳定 Release；无 Release 时 GitHub 返回 404。 */
    suspend fun latestRelease(
        owner: String,
        name: String,
    ): NetworkResult<ReleaseInfo> =
        client.request(
            path = "/repos/${owner.encodeURLPath()}/${name.encodeURLPath()}/releases/latest",
            mapper = { response: HttpResponse ->
                GitHubJson.decode(ReleaseDto.serializer(), response.bodyAsText()).toDomain()
            },
        )

    /**
     * 拉取任意仓库的多版本 Release 列表（v0.8.0 M14）。
     * GET /repos/{owner}/{repo}/releases：按发布时间倒序，含 draft/prerelease 由调用方过滤。
     * 每页条数客户端固定，不解析 Link header（首屏足够，翻页由 UI 页码驱动）。
     */
    suspend fun listReleases(
        owner: String,
        name: String,
        page: Int,
        perPage: Int,
    ): NetworkResult<List<ReleaseInfo>> =
        client.request(
            path =
                "/repos/${owner.encodeURLPath()}/${name.encodeURLPath()}/releases" +
                    "?per_page=$perPage&page=$page",
            mapper = { response: HttpResponse ->
                GitHubJson
                    .decode(ListSerializer(ReleaseDto.serializer()), response.bodyAsText())
                    .map { it.toDomain() }
            },
        )

    /** 拉取任意仓库的 Git Tag 列表（v0.8.0 M14）。GET /repos/{owner}/{repo}/tags。 */
    suspend fun listTags(
        owner: String,
        name: String,
        perPage: Int,
    ): NetworkResult<List<TagInfo>> =
        client.request(
            path = "/repos/${owner.encodeURLPath()}/${name.encodeURLPath()}/tags?per_page=$perPage",
            mapper = { response: HttpResponse ->
                GitHubJson.decode(ListSerializer(TagDto.serializer()), response.bodyAsText()).map { it.toDomain() }
            },
        )
}
