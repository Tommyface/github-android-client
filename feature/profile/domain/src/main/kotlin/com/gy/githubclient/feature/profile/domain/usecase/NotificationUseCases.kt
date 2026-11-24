/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.domain.usecase

import com.gy.githubclient.core.domain.network.NetworkResult
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
