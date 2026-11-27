/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.data.remote

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.data.network.GitHubJson
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.core.mutation.MutationResult
import com.gy.githubclient.core.mutation.MutationSpec
import com.gy.githubclient.feature.profile.domain.model.GistItem
import com.gy.githubclient.feature.profile.domain.model.GitHubNotification
import com.gy.githubclient.feature.profile.domain.model.OrgMember
import com.gy.githubclient.feature.profile.domain.model.OrgSummary
import com.gy.githubclient.feature.profile.domain.model.UserEvent
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

    /** 一键全部标记已读：PUT /notifications（205 Reset Content）。 */
    suspend fun markAllNotificationsRead(): NetworkResult<Unit> =
        client.request(
            method = HttpMethod.Put,
            path = "/notifications",
            mapper = { _: HttpResponse -> Unit },
        )

    /** 查询是否已关注某用户：204=已关注，404=未关注。 */
    suspend fun isFollowing(login: String): NetworkResult<Boolean> =
        client.request(
            path = "/user/following/$login",
            mapper = { _: HttpResponse -> true },
        )

    /** 关注（PUT）/ 取关（DELETE）；需要 `user:follow` scope。 */
    suspend fun setFollowing(
        login: String,
        following: Boolean,
    ): MutationResult<Unit> =
        client.mutate(
            spec = MutationSpec(action = if (following) "follow" else "unfollow", target = "@$login"),
            method = if (following) HttpMethod.Put else HttpMethod.Delete,
            path = "/user/following/$login",
            mapper = { _: HttpResponse -> Unit },
        )

    /** 忽略（PUT ignored=true）/ 恢复（DELETE）通知线程订阅。 */
    suspend fun setThreadSubscription(
        threadId: Long,
        ignored: Boolean,
    ): MutationResult<Unit> =
        client.mutate(
            spec = MutationSpec(action = if (ignored) "notification.ignore" else "notification.subscribe", target = "thread/$threadId"),
            method = if (ignored) HttpMethod.Put else HttpMethod.Delete,
            path = "/notifications/threads/$threadId/subscription",
            body = if (ignored) IGNORED_TRUE else null,
            mapper = { _: HttpResponse -> Unit },
        )

    /** 拉取用户公开动态事件（v0.10.0 N4）。 */
    suspend fun listUserEvents(login: String): NetworkResult<List<UserEvent>> =
        client.request(
            path = "/users/$login/events/public?per_page=$EVENTS_PER_PAGE",
            mapper = { response: HttpResponse ->
                GitHubJson
                    .decode(ListSerializer(EventDto.serializer()), response.bodyAsText())
                    .map { it.toDomain() }
            },
        )

    /** 组织概要（v0.15.0 A5）。 */
    suspend fun getOrg(login: String): NetworkResult<OrgSummary> =
        client.request(
            path = "/orgs/$login",
            mapper = { response: HttpResponse ->
                GitHubJson.decode(OrgDto.serializer(), response.bodyAsText()).toDomain()
            },
        )

    /** 组织成员（v0.15.0 A5）。 */
    suspend fun listOrgMembers(login: String): NetworkResult<List<OrgMember>> =
        client.request(
            path = "/orgs/$login/members?per_page=$ORG_MEMBERS_PER_PAGE",
            mapper = { response: HttpResponse ->
                GitHubJson
                    .decode(ListSerializer(OrgMemberDto.serializer()), response.bodyAsText())
                    .map { it.toDomain() }
            },
        )

    /** 用户 Gist 列表（v0.15.0 A6）。 */
    suspend fun listUserGists(login: String): NetworkResult<List<GistItem>> =
        client.request(
            path = "/users/$login/gists?per_page=$GISTS_PER_PAGE",
            mapper = { response: HttpResponse ->
                GitHubJson
                    .decode(ListSerializer(GistDto.serializer()), response.bodyAsText())
                    .map { it.toDomain() }
            },
        )

    /** 单条 Gist 详情（v0.15.0 A6）。 */
    suspend fun getGist(id: String): NetworkResult<GistItem> =
        client.request(
            path = "/gists/$id",
            mapper = { response: HttpResponse ->
                GitHubJson.decode(GistDto.serializer(), response.bodyAsText()).toDomain()
            },
        )

    private companion object {
        const val PROFILE_PATH = "/user"
        const val NOTIFICATIONS_PER_PAGE = 50
        const val IGNORED_TRUE = """{"ignored":true}"""
        const val EVENTS_PER_PAGE = 20
        const val ORG_MEMBERS_PER_PAGE = 100
        const val GISTS_PER_PAGE = 50
    }
}
