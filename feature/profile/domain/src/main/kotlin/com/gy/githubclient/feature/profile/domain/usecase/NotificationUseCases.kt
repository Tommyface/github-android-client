/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.domain.usecase

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.core.mutation.MutationResult
import com.gy.githubclient.feature.profile.domain.model.GitHubNotification
import com.gy.githubclient.feature.profile.domain.repository.ProfileRepository

/** 拉取通知列表（v0.6.0 M7）。 */
class ListNotificationsUseCase(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(all: Boolean = false): NetworkResult<List<GitHubNotification>> =
        repository.listNotifications(all)
}

/** 标记单条通知线程已读。 */
class MarkNotificationReadUseCase(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(threadId: Long): NetworkResult<Unit> = repository.markThreadRead(threadId)
}

/** 一键全部标记已读（githup 通知批量处理等价能力）。 */
class MarkAllNotificationsReadUseCase(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(): NetworkResult<Unit> = repository.markAllNotificationsRead()
}

/** 查询是否已关注某用户。 */
class IsFollowingUseCase(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(login: String): NetworkResult<Boolean> = repository.isFollowing(login)
}

/** 关注 / 取关用户。 */
class SetFollowingUseCase(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(
        login: String,
        following: Boolean,
    ): MutationResult<Unit> = repository.setFollowing(login, following)
}

/** 忽略 / 恢复通知线程订阅。 */
class SetThreadSubscriptionUseCase(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(
        threadId: Long,
        ignored: Boolean,
    ): MutationResult<Unit> = repository.setThreadSubscription(threadId, ignored)
}
