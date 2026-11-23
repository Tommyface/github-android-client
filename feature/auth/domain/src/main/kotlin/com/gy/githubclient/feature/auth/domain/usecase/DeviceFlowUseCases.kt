/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.domain.usecase

import com.gy.githubclient.feature.auth.domain.model.DeviceFlowPollResult
import com.gy.githubclient.feature.auth.domain.model.DeviceFlowStartResult
import com.gy.githubclient.feature.auth.domain.repository.OAuthDeviceFlowRepository

/** 发起 OAuth Device Flow：获取 user_code / device_code。 */
class StartDeviceFlowUseCase(
    private val repository: OAuthDeviceFlowRepository,
) {
    suspend operator fun invoke(): DeviceFlowStartResult = repository.startDeviceFlow()
}

/** 单次轮询 OAuth Device Flow 令牌端点；轮询节奏由调用方控制。 */
class PollDeviceTokenUseCase(
    private val repository: OAuthDeviceFlowRepository,
) {
    suspend operator fun invoke(deviceCode: String): DeviceFlowPollResult = repository.pollForToken(deviceCode)
}
