/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.domain.repository

import com.gy.githubclient.feature.auth.domain.model.DeviceFlowPollResult
import com.gy.githubclient.feature.auth.domain.model.DeviceFlowStartResult

/**
 * OAuth Device Flow 仓库契约。
 *
 * 与 [AuthRepository] 分离：Device Flow 只负责「换 token」，
 * 换到后的校验/存储/会话发布复用 AuthRepository.signInWithOAuth 管线。
 */
interface OAuthDeviceFlowRepository {
    /** 请求 device_code 与 user_code（第一步）。 */
    suspend fun startDeviceFlow(): DeviceFlowStartResult

    /** 用 device_code 轮询 access_token（第二步，单次调用）。 */
    suspend fun pollForToken(deviceCode: String): DeviceFlowPollResult
}
