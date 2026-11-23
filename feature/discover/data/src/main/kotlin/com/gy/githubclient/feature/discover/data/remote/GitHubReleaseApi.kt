/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.data.remote

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.discover.domain.model.ReleaseInfo
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.encodeURLPath

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
            mapper = { response: HttpResponse -> response.body<ReleaseDto>().toDomain() },
        )
}
