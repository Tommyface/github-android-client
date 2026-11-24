/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.data

import com.gy.githubclient.feature.auth.data.local.SecureTokenStore
import com.gy.githubclient.feature.auth.data.remote.GitHubAuthApi
import com.gy.githubclient.feature.auth.data.remote.GitHubUserDto
import com.gy.githubclient.feature.auth.data.remote.RemoteUserResult
import com.gy.githubclient.feature.auth.domain.model.AuthMethod
import com.gy.githubclient.feature.auth.domain.model.AuthResult
import com.gy.githubclient.feature.auth.domain.model.AuthSession
import com.gy.githubclient.feature.auth.domain.model.AuthState
import com.gy.githubclient.feature.auth.domain.model.AuthUser
import com.gy.githubclient.feature.auth.domain.model.TokenChannel
import com.gy.githubclient.feature.auth.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 认证仓库实现：
 * - token 明文只存在于 [SecureTokenStore]（EncryptedSharedPreferences）。
 * - 领域模型只暴露「是否就绪」与用户画像，不泄露 token。
 * - [MutableStateFlow] 保存已登录用户画像，token 变更时重建会话快照。
 */
class AuthRepositoryImpl(
    private val tokenStore: SecureTokenStore,
    private val api: GitHubAuthApi,
) : AuthRepository {
    private val userState = MutableStateFlow<AuthUser?>(null)

    private val mutableAuthState = MutableStateFlow<AuthState>(AuthState.Loading)

    override fun observeAuthState(): Flow<AuthState> = mutableAuthState.asStateFlow()

    override suspend fun currentSession(): AuthSession = buildSession()

    override suspend fun restoreSession(): AuthState {
        val primaryToken = tokenStore.getToken(TokenChannel.PRIMARY)
        if (primaryToken == null) {
            mutableAuthState.value = AuthState.SignedOut
            return AuthState.SignedOut
        }
        // 有 token 但内存无画像（冷启动）时，用 token 反查用户。
        if (userState.value == null) {
            when (val result = api.getCurrentUser(primaryToken)) {
                is RemoteUserResult.Success -> userState.value = result.user.toDomain()
                else -> Unit // token 失效由后续 API 调用触发 401 时统一清理
            }
        }
        publish()
        return mutableAuthState.value
    }

    override suspend fun signInWithPat(token: String): AuthResult = signIn(token, AuthMethod.PAT)

    override suspend fun signInWithOAuth(
        accessToken: String,
        scopes: List<String>,
    ): AuthResult = signIn(accessToken, AuthMethod.OAUTH, scopes)

    private suspend fun signIn(
        token: String,
        method: AuthMethod,
        scopes: List<String> = emptyList(),
    ): AuthResult =
        when (val result = api.getCurrentUser(token)) {
            is RemoteUserResult.Success -> {
                tokenStore.saveToken(TokenChannel.PRIMARY, token)
                tokenStore.setAuthMethod(method)
                if (method == AuthMethod.OAUTH) tokenStore.saveScopes(scopes)
                userState.value = result.user.toDomain()
                publish()
                AuthResult.Success(buildSession())
            }

            is RemoteUserResult.Unauthorized ->
                AuthResult.Failure.InvalidToken(TokenChannel.PRIMARY)

            is RemoteUserResult.RateLimited ->
                AuthResult.Failure.RateLimited(result.retryAfter)

            is RemoteUserResult.Error ->
                AuthResult.Failure.Network(result.message)
        }

    override suspend fun bindNotificationToken(token: String): AuthResult =
        when (val result = api.getCurrentUser(token)) {
            is RemoteUserResult.Success -> {
                tokenStore.saveToken(TokenChannel.NOTIFICATION, token)
                publish()
                AuthResult.Success(buildSession())
            }

            is RemoteUserResult.Unauthorized ->
                AuthResult.Failure.InvalidToken(TokenChannel.NOTIFICATION)

            is RemoteUserResult.RateLimited ->
                AuthResult.Failure.RateLimited(result.retryAfter)

            is RemoteUserResult.Error ->
                AuthResult.Failure.Network(result.message)
        }

    override suspend fun getPrimaryToken(): String? = tokenStore.getToken(TokenChannel.PRIMARY)

    override suspend fun getNotificationToken(): String? =
        tokenStore.getToken(TokenChannel.NOTIFICATION)

    override suspend fun signOut(channel: TokenChannel?) {
        if (channel == null) {
            tokenStore.clearAll()
            userState.value = null
        } else {
            tokenStore.clearToken(channel)
            if (channel == TokenChannel.PRIMARY) userState.value = null
        }
        publish()
    }

    override suspend fun setAuthMethod(method: AuthMethod) = tokenStore.setAuthMethod(method)

    private fun buildSession(): AuthSession =
        AuthSession(
            user = userState.value,
            hasPrimaryToken = tokenStore.getToken(TokenChannel.PRIMARY) != null,
            hasNotificationToken = tokenStore.getToken(TokenChannel.NOTIFICATION) != null,
            method = tokenStore.getAuthMethod(),
            scopes = tokenStore.getScopes(),
        )

    private fun publish() {
        val session = buildSession()
        mutableAuthState.value =
            if (session.isSignedIn) {
                AuthState.SignedIn(session)
            } else {
                AuthState.SignedOut
            }
    }

    private fun GitHubUserDto.toDomain(): AuthUser =
        AuthUser(
            login = login,
            displayName = displayName,
            avatarUrl = avatarUrl,
            htmlUrl = htmlUrl,
        )
}
