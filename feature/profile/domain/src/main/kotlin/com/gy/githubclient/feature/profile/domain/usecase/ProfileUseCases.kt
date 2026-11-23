/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.domain.usecase

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.profile.domain.model.UserProfile
import com.gy.githubclient.feature.profile.domain.repository.ProfileRepository
import kotlinx.coroutines.flow.Flow

/** 观察当前用户画像缓存。 */
class ObserveProfileUseCase(
    private val repository: ProfileRepository,
) {
    operator fun invoke(): Flow<UserProfile?> = repository.observeProfile()
}

/** 刷新当前用户画像。 */
class RefreshProfileUseCase(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(): NetworkResult<UserProfile> = repository.refreshProfile()
}

/** 清空画像缓存（登出时）。 */
class ClearProfileCacheUseCase(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke() = repository.clearCache()
}
