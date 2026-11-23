/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.domain.usecase

import com.gy.githubclient.feature.auth.domain.model.AuthSession
import com.gy.githubclient.feature.auth.domain.model.AuthState
import com.gy.githubclient.feature.auth.domain.model.TokenChannel
import com.gy.githubclient.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow

/** 观察认证状态。 */
class ObserveAuthStateUseCase(
    private val repository: AuthRepository,
) {
    operator fun invoke(): Flow<AuthState> = repository.observeAuthState()
}

/** 读取当前会话快照。 */
class GetCurrentSessionUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(): AuthSession = repository.currentSession()
}

/** 登出：默认清全部通道，也可只清某一个。 */
class SignOutUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(channel: TokenChannel? = null) = repository.signOut(channel)
}
