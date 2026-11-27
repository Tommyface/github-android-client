/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.data

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.core.mutation.MutationResult
import com.gy.githubclient.feature.profile.data.local.ProfileDao
import com.gy.githubclient.feature.profile.data.local.toDomain
import com.gy.githubclient.feature.profile.data.local.toEntity
import com.gy.githubclient.feature.profile.data.remote.GitHubUserApi
import com.gy.githubclient.feature.profile.domain.model.GistItem
import com.gy.githubclient.feature.profile.domain.model.GitHubNotification
import com.gy.githubclient.feature.profile.domain.model.OrgMember
import com.gy.githubclient.feature.profile.domain.model.OrgSummary
import com.gy.githubclient.feature.profile.domain.model.UserEvent
import com.gy.githubclient.feature.profile.domain.model.UserProfile
import com.gy.githubclient.feature.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 个人资料仓库实现（Room 为单一数据源）：刷新回填缓存，失败保留旧数据。 */
class ProfileRepositoryImpl(
    private val dao: ProfileDao,
    private val api: GitHubUserApi,
) : ProfileRepository {
    override fun observeProfile(): Flow<UserProfile?> = dao.observeProfile().map { it?.toDomain() }

    override suspend fun refreshProfile(): NetworkResult<UserProfile> =
        when (val result = api.getCurrentUser()) {
            is NetworkResult.Success -> {
                dao.upsert(result.data.toEntity())
                result
            }

            is NetworkResult.Failure -> result
        }

    override suspend fun listNotifications(all: Boolean): NetworkResult<List<GitHubNotification>> =
        api.listNotifications(all)

    override suspend fun markThreadRead(threadId: Long): NetworkResult<Unit> = api.markThreadRead(threadId)

    override suspend fun markAllNotificationsRead(): NetworkResult<Unit> = api.markAllNotificationsRead()

    override suspend fun isFollowing(login: String): NetworkResult<Boolean> =
        when (val result = api.isFollowing(login)) {
            is NetworkResult.Success -> result
            is NetworkResult.Failure ->
                if (result.error is com.gy.githubclient.core.domain.network.ApiError.NotFound) {
                    NetworkResult.Success(false)
                } else {
                    result
                }
        }

    override suspend fun setFollowing(
        login: String,
        following: Boolean,
    ): MutationResult<Unit> = api.setFollowing(login, following)

    override suspend fun setThreadSubscription(
        threadId: Long,
        ignored: Boolean,
    ): MutationResult<Unit> = api.setThreadSubscription(threadId, ignored)

    override suspend fun listUserEvents(login: String): NetworkResult<List<UserEvent>> = api.listUserEvents(login)

    override suspend fun getOrg(login: String): NetworkResult<OrgSummary> = api.getOrg(login)

    override suspend fun listOrgMembers(login: String): NetworkResult<List<OrgMember>> = api.listOrgMembers(login)

    override suspend fun listUserGists(login: String): NetworkResult<List<GistItem>> = api.listUserGists(login)

    override suspend fun getGist(id: String): NetworkResult<GistItem> = api.getGist(id)

    override suspend fun clearCache() = dao.clear()
}
