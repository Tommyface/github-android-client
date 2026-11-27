/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.domain.repository

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.core.mutation.MutationResult
import com.gy.githubclient.feature.profile.domain.model.GistItem
import com.gy.githubclient.feature.profile.domain.model.GitHubNotification
import com.gy.githubclient.feature.profile.domain.model.OrgMember
import com.gy.githubclient.feature.profile.domain.model.OrgSummary
import com.gy.githubclient.feature.profile.domain.model.UserEvent
import com.gy.githubclient.feature.profile.domain.model.UserProfile
import kotlinx.coroutines.flow.Flow

/**
 * 个人资料仓库契约：UI 只观察缓存流，刷新由网络层回填缓存。
 *
 * - [observeProfile] 暴露 Room 缓存的 Flow，离线也有数据。
 * - [refreshProfile] 走统一网络层；网络失败时保留旧缓存。
 */
interface ProfileRepository {
    /** 观察本地缓存的用户画像。 */
    fun observeProfile(): Flow<UserProfile?>

    /** 刷新用户画像并回填缓存，成功返回最新画像。 */
    suspend fun refreshProfile(): NetworkResult<UserProfile>

    /**
     * 拉取未读通知列表（v0.6.0 M7）。
     * [all] = false 只取未读；412/403（权限体检中已探明无 Notifications 读权限）如实上抛。
     */
    suspend fun listNotifications(all: Boolean = false): NetworkResult<List<GitHubNotification>>

    /** 标记单条通知线程已读；成功与否以 [NetworkResult] 表达。 */
    suspend fun markThreadRead(threadId: Long): NetworkResult<Unit>

    /** 一键全部标记已读（githup 通知批量处理等价能力）：PUT /notifications。 */
    suspend fun markAllNotificationsRead(): NetworkResult<Unit>

    /** 拉取用户公开动态事件（v0.10.0 N4）。 */
    suspend fun listUserEvents(login: String): NetworkResult<List<UserEvent>>

    /** 组织概要（v0.15.0 A5）：GET /orgs/{org}，只读。 */
    suspend fun getOrg(login: String): NetworkResult<OrgSummary>

    /** 组织成员（v0.15.0 A5）：GET /orgs/{org}/members，只读。 */
    suspend fun listOrgMembers(login: String): NetworkResult<List<OrgMember>>

    /** 用户 Gist 列表（v0.15.0 A6）：GET /users/{login}/gists，只读。 */
    suspend fun listUserGists(login: String): NetworkResult<List<GistItem>>

    /** 单条 Gist 详情（v0.15.0 A6）：GET /gists/{id}，只读。 */
    suspend fun getGist(id: String): NetworkResult<GistItem>

    /** 查询是否已关注某用户（204=已关注，404=未关注）。 */
    suspend fun isFollowing(login: String): NetworkResult<Boolean>

    /** 关注 / 取关用户；需要 OAuth `user:follow` scope。 */
    suspend fun setFollowing(
        login: String,
        following: Boolean,
    ): MutationResult<Unit>

    /** 忽略 / 恢复某条通知线程（PUT/DELETE subscription）。 */
    suspend fun setThreadSubscription(
        threadId: Long,
        ignored: Boolean,
    ): MutationResult<Unit>

    /** 清空本地缓存（登出时调用，防止串号）。 */
    suspend fun clearCache()
}
