/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.data

import com.gy.githubclient.core.domain.network.ApiChannel
import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.feature.auth.domain.model.TokenChannel
import com.gy.githubclient.feature.auth.domain.repository.AuthRepository

/**
 * 把 core 网络层的 [AuthTokenProvider] 接口接到 auth 仓库实现。
 * 这是依赖倒置的落地：core 不依赖 auth，由 auth 提供 token 来源。
 */
class AuthTokenProviderImpl(
    private val repository: AuthRepository,
) : AuthTokenProvider {
    override suspend fun tokenFor(channel: ApiChannel): String? =
        when (channel) {
            ApiChannel.PRIMARY -> repository.getPrimaryToken()
            ApiChannel.NOTIFICATION -> repository.getNotificationToken()
        }

    override suspend fun onUnauthorized(channel: ApiChannel) {
        // 401 只清理该通道 token；主通道失效会触发会话状态变为 SignedOut。
        repository.signOut(channel.toTokenChannel())
    }

    private fun ApiChannel.toTokenChannel(): TokenChannel =
        when (this) {
            ApiChannel.PRIMARY -> TokenChannel.PRIMARY
            ApiChannel.NOTIFICATION -> TokenChannel.NOTIFICATION
        }
}
