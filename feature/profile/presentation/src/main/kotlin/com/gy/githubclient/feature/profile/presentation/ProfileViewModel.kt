/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.auth.domain.usecase.SignOutUseCase
import com.gy.githubclient.feature.profile.domain.model.UserProfile
import com.gy.githubclient.feature.profile.domain.usecase.ClearProfileCacheUseCase
import com.gy.githubclient.feature.profile.domain.usecase.ObserveProfileUseCase
import com.gy.githubclient.feature.profile.domain.usecase.RefreshProfileUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 个人资料 UI 的一次性事件。 */
sealed interface ProfileEvent {
    data class ShowMessage(
        val message: String,
    ) : ProfileEvent
}

/**
 * 个人资料 ViewModel：
 * - [profile] 观察 Room 缓存，离线可见；刷新失败以事件提示。
 * - [signOut] 同时清理画像缓存与会话，MainActivity 由状态流自动切回登录页。
 */
class ProfileViewModel(
    private val observeProfile: ObserveProfileUseCase,
    private val refreshProfile: RefreshProfileUseCase,
    private val clearProfileCache: ClearProfileCacheUseCase,
    private val signOutUseCase: SignOutUseCase,
) : ViewModel() {
    val profile: StateFlow<UserProfile?> =
        observeProfile()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = null,
            )

    private val _events = Channel<ProfileEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            when (val result = refreshProfile()) {
                is NetworkResult.Success -> Unit
                is NetworkResult.Failure -> _events.trySend(ProfileEvent.ShowMessage(errorMessage(result.error)))
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            clearProfileCache()
            signOutUseCase()
        }
    }

    private fun errorMessage(error: ApiError): String =
        when (error) {
            ApiError.Unauthorized -> "登录已失效，请重新登录"
            is ApiError.RateLimited -> "API 限流，请稍后再试"
            ApiError.NotFound -> "资源不存在"
            is ApiError.Network -> "网络错误：${error.cause}"
            is ApiError.Http -> "请求失败（${error.code}）"
            is ApiError.Unknown -> "未知错误：${error.message}"
        }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
