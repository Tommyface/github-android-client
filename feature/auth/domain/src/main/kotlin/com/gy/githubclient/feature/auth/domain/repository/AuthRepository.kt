/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.domain.repository

import com.gy.githubclient.feature.auth.domain.model.AuthMethod
import com.gy.githubclient.feature.auth.domain.model.AuthResult
import com.gy.githubclient.feature.auth.domain.model.AuthSession
import com.gy.githubclient.feature.auth.domain.model.AuthState
import com.gy.githubclient.feature.auth.domain.model.TokenChannel
import kotlinx.coroutines.flow.Flow

/**
 * 认证仓库：token 的安全存取与会话状态。
 *
 * 领域层只定义契约，具体实现（Keystore + Ktor）位于 data 层。
 */
interface AuthRepository {
    /** 会话状态流，UI 与用例据此驱动。 */
    fun observeAuthState(): Flow<AuthState>

    /** 读取当前会话快照（含 token 是否就绪，不含 token 明文）。 */
    suspend fun currentSession(): AuthSession

    /**
     * 从安全存储恢复会话：若存在主 token，则用其拉取用户画像并发布已登录状态。
     * App 冷启动时调用一次。返回恢复后的状态。
     */
    suspend fun restoreSession(): AuthState

    /** 用 fine-grained PAT 登录主通道。 */
    suspend fun signInWithPat(token: String): AuthResult

    /**
     * 通过 OAuth 交换到的 access token 登录主通道。
     * [scopes] 为 Device Flow 响应中 GitHub 实际授予的 scope 列表，持久化供权限体检展示。
     */
    suspend fun signInWithOAuth(
        accessToken: String,
        scopes: List<String> = emptyList(),
    ): AuthResult

    /**
     * 绑定 classic PAT 到通知通道。
     * 通知 API 只认 classic PAT，故与主通道分离存储。
     */
    suspend fun bindNotificationToken(token: String): AuthResult

    /** 读取主通道 token（供网络拦截器注入），未登录返回 null。 */
    suspend fun getPrimaryToken(): String?

    /** 读取通知通道 token，未绑定返回 null。 */
    suspend fun getNotificationToken(): String?

    /** 清除指定通道 token；不传则全部清除。 */
    suspend fun signOut(channel: TokenChannel? = null)

    /** 记录本次使用的认证方式。 */
    suspend fun setAuthMethod(method: AuthMethod)
}
