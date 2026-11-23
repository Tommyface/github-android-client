/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.domain.repository

import com.gy.githubclient.core.domain.network.NetworkResult
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

    /** 清空本地缓存（登出时调用，防止串号）。 */
    suspend fun clearCache()
}
