/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.domain.usecase

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.profile.domain.model.UserEvent
import com.gy.githubclient.feature.profile.domain.repository.ProfileRepository

/** 拉取当前用户公开动态事件（v0.10.0 N4）。 */
class ListUserEventsUseCase(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(login: String): NetworkResult<List<UserEvent>> = repository.listUserEvents(login)
}
