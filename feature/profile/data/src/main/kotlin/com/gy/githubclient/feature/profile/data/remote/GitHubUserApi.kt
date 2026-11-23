/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.data.remote

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.profile.domain.model.UserProfile
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse

/** 用户 API：仅负责把统一网络层响应解析为领域模型。 */
class GitHubUserApi(
    private val client: GitHubApiClient,
) {
    /** 获取当前登录用户画像。 */
    suspend fun getCurrentUser(): NetworkResult<UserProfile> =
        client.request(
            path = PROFILE_PATH,
            mapper = { response: HttpResponse -> response.body<UserDto>().toDomain() },
        )

    private companion object {
        const val PROFILE_PATH = "/user"
    }
}
