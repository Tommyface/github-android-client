/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.data.remote

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.data.network.GitHubJson
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.profile.domain.model.GitHubNotification
import com.gy.githubclient.feature.profile.domain.model.UserProfile
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import kotlinx.serialization.builtins.ListSerializer

/** 用户 API：仅负责把统一网络层响应解析为领域模型。 */
class GitHubUserApi(
    private val client: GitHubApiClient,
) {
    /** 获取当前登录用户画像。 */
    suspend fun getCurrentUser(): NetworkResult<UserProfile> =
        client.request(
            path = PROFILE_PATH,
            mapper = { response: HttpResponse ->
                GitHubJson.decode(UserDto.serializer(), response.bodyAsText()).toDomain()
            },
        )

    /** 拉取通知列表（v0.6.0 M7）：all=false 只取未读。 */
    suspend fun listNotifications(all: Boolean): NetworkResult<List<GitHubNotification>> =
        client.request(
            path = "/notifications?per_page=$NOTIFICATIONS_PER_PAGE${if (all) "&all=true" else ""}",
            mapper = { response: HttpResponse ->
                GitHubJson
                    .decode(ListSerializer(NotificationDto.serializer()), response.bodyAsText())
                    .map { it.toDomain() }
            },
        )

    /** 标记单条通知线程已读（205 Reset Content 表示成功）。 */
    suspend fun markThreadRead(threadId: Long): NetworkResult<Unit> =
        client.request(
            method = HttpMethod.Patch,
            path = "/notifications/threads/$threadId",
            mapper = { _: HttpResponse -> Unit },
        )

    private companion object {
        const val PROFILE_PATH = "/user"
        const val NOTIFICATIONS_PER_PAGE = 50
    }
}
