/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.domain.usecase

import com.gy.githubclient.feature.auth.domain.model.AuthMethod
import com.gy.githubclient.feature.auth.domain.model.AuthResult
import com.gy.githubclient.feature.auth.domain.repository.AuthRepository

/** 用 fine-grained PAT 登录主通道。 */
class SignInWithPatUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(token: String): AuthResult {
        val trimmed = token.trim()
        if (trimmed.isEmpty()) {
            return AuthResult.Failure.InvalidToken(
                com.gy.githubclient.feature.auth.domain.model.TokenChannel.PRIMARY,
            )
        }
        val result = repository.signInWithPat(trimmed)
        if (result is AuthResult.Success) repository.setAuthMethod(AuthMethod.PAT)
        return result
    }
}

/** 用 OAuth access token 登录主通道，[scopes] 为 GitHub 实际授予的 scope 列表。 */
class SignInWithOAuthUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(
        accessToken: String,
        scopes: List<String> = emptyList(),
    ): AuthResult {
        val result = repository.signInWithOAuth(accessToken.trim(), scopes)
        if (result is AuthResult.Success) repository.setAuthMethod(AuthMethod.OAUTH)
        return result
    }
}

/** 绑定 classic PAT 到通知通道（可选步骤，失败不影响主通道登录）。 */
class BindNotificationTokenUseCase(
    private val repository: AuthRepository,
) {
    suspend operator fun invoke(token: String): AuthResult =
        repository.bindNotificationToken(token.trim())
}
