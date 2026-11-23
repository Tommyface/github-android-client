/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.starred.data.remote

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.domain.model.Repository
import com.gy.githubclient.feature.starred.domain.model.StarredFilter
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse

/** 星标 REST API；认证、限流退避均由统一网络层处理。 */
class GitHubStarredApi(
    private val client: GitHubApiClient,
) {
    /** 拉取星标仓库列表。 */
    suspend fun listStarred(filter: StarredFilter): NetworkResult<List<Repository>> =
        client.request(
            path = listPath(filter),
            mapper = { response: HttpResponse ->
                response.body<List<StarredRepoDto>>().map { it.toDomain() }
            },
        )

    /** 移除星标（DELETE 返回 204 无响应体）。 */
    suspend fun removeStar(
        owner: String,
        name: String,
    ): NetworkResult<Unit> =
        client.request(
            method = io.ktor.http.HttpMethod.Delete,
            path = "/user/starred/$owner/$name",
            mapper = { _: HttpResponse -> Unit },
        )

    private fun listPath(filter: StarredFilter): String =
        "/user/starred?sort=${filter.sort.name.lowercase()}&page=${filter.page}&per_page=${filter.perPage}"
}
