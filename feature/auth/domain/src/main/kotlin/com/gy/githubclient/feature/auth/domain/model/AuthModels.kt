/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.domain.model

/**
 * 认证通道类型。
 *
 * - [PRIMARY]：主通道，fine-grained PAT / OAuth token，负责绝大多数 API。
 * - [NOTIFICATION]：通知通道，classic PAT，仅用于 /notifications 系列端点。
 */
enum class TokenChannel {
    PRIMARY,
    NOTIFICATION,
}

/** 认证方式。 */
enum class AuthMethod {
    PAT,
    OAUTH,
}

/** 登录用户的最小画像。 */
data class AuthUser(
    val login: String,
    val displayName: String?,
    val avatarUrl: String?,
    val htmlUrl: String,
)

/**
 * 会话快照：两类 token 是否就绪 + 已登录用户。
 * token 明文永不进入该模型，仅保存在安全存储中。
 */
data class AuthSession(
    val user: AuthUser?,
    val hasPrimaryToken: Boolean,
    val hasNotificationToken: Boolean,
    val method: AuthMethod?,
) {
    val isSignedIn: Boolean get() = hasPrimaryToken && user != null
}

/** 认证状态机。 */
sealed interface AuthState {
    data object Loading : AuthState

    data object SignedOut : AuthState

    data class SignedIn(
        val session: AuthSession,
    ) : AuthState
}

/** 认证结果，用于 UI 层区分成功与可展示的错误。 */
sealed interface AuthResult {
    data class Success(
        val session: AuthSession,
    ) : AuthResult

    sealed interface Failure : AuthResult {
        data class InvalidToken(
            val channel: TokenChannel,
        ) : Failure

        data class RateLimited(
            val retryAfterSeconds: Long?,
        ) : Failure

        data class Network(
            val cause: String,
        ) : Failure

        data object UnsupportedScope : Failure
    }
}
