/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.domain.usecase

import com.gy.githubclient.feature.auth.domain.model.AuthState
import com.gy.githubclient.feature.auth.domain.repository.AuthRepository

/** 冷启动恢复会话：有主 token 则用其反查用户画像并发布状态。 */
class RestoreSessionUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(): AuthState = repository.restoreSession()
}
