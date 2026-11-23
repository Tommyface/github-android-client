/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.feature.auth.domain.model.AuthResult
import com.gy.githubclient.feature.auth.domain.model.AuthState
import com.gy.githubclient.feature.auth.domain.model.TokenChannel
import com.gy.githubclient.feature.auth.domain.usecase.BindNotificationTokenUseCase
import com.gy.githubclient.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.gy.githubclient.feature.auth.domain.usecase.RestoreSessionUseCase
import com.gy.githubclient.feature.auth.domain.usecase.SignInWithPatUseCase
import com.gy.githubclient.feature.auth.domain.usecase.SignOutUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 登录表单与一次性事件。 */
data class AuthUiState(
    val primaryToken: String = "",
    val notificationToken: String = "",
    val isSubmitting: Boolean = false,
    val isBindingNotification: Boolean = false,
)

sealed interface AuthEvent {
    data class ShowMessage(
        val message: String,
    ) : AuthEvent

    data object LoginSuccess : AuthEvent

    data object NotificationBound : AuthEvent
}

/**
 * 认证界面 ViewModel。
 * - [authState] 来自仓库的状态流（冷启动恢复后由 init 触发）。
 * - [uiState] 仅表单瞬时状态；token 明文只在内存停留到提交，随后交给安全存储。
 */
class AuthViewModel(
    private val signInWithPat: SignInWithPatUseCase,
    private val bindNotificationToken: BindNotificationTokenUseCase,
    observeAuthState: ObserveAuthStateUseCase,
    private val signOut: SignOutUseCase,
    private val restoreSession: RestoreSessionUseCase,
) : ViewModel() {
    val authState: StateFlow<AuthState> =
        observeAuthState()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = AuthState.Loading,
            )

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    private val _events = Channel<AuthEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch { restoreSession() }
    }

    fun onPrimaryTokenChange(value: String) {
        _uiState.value = _uiState.value.copy(primaryToken = value)
    }

    fun onNotificationTokenChange(value: String) {
        _uiState.value = _uiState.value.copy(notificationToken = value)
    }

    fun signIn() {
        val token = _uiState.value.primaryToken
        if (token.isBlank()) {
            _events.trySend(AuthEvent.ShowMessage("请输入 Fine-grained Token"))
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSubmitting = true)
            when (val result = signInWithPat(token)) {
                is AuthResult.Success -> {
                    _events.trySend(AuthEvent.LoginSuccess)
                    _uiState.value = _uiState.value.copy(primaryToken = "")
                }

                is AuthResult.Failure.InvalidToken ->
                    _events.trySend(AuthEvent.ShowMessage("Token 无效或已过期，请检查后重试"))

                is AuthResult.Failure.RateLimited ->
                    _events.trySend(AuthEvent.ShowMessage("API 限流，请稍后再试"))

                is AuthResult.Failure.Network ->
                    _events.trySend(AuthEvent.ShowMessage("网络错误：${result.cause}"))

                is AuthResult.Failure.UnsupportedScope ->
                    _events.trySend(AuthEvent.ShowMessage("Token 权限不足"))
            }
            _uiState.value = _uiState.value.copy(isSubmitting = false)
        }
    }

    fun bindNotification() {
        val token = _uiState.value.notificationToken
        if (token.isBlank()) {
            _events.trySend(AuthEvent.ShowMessage("请输入 Classic Token"))
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBindingNotification = true)
            when (val result = bindNotificationToken(token)) {
                is AuthResult.Success -> {
                    _events.trySend(AuthEvent.NotificationBound)
                    _uiState.value = _uiState.value.copy(notificationToken = "")
                }

                is AuthResult.Failure.InvalidToken ->
                    _events.trySend(AuthEvent.ShowMessage("通知通道 Token 无效"))

                is AuthResult.Failure.RateLimited ->
                    _events.trySend(AuthEvent.ShowMessage("API 限流，请稍后再试"))

                is AuthResult.Failure.Network ->
                    _events.trySend(AuthEvent.ShowMessage("网络错误：${result.cause}"))

                is AuthResult.Failure.UnsupportedScope ->
                    _events.trySend(AuthEvent.ShowMessage("Token 权限不足"))
            }
            _uiState.value = _uiState.value.copy(isBindingNotification = false)
        }
    }

    fun signOut(channel: TokenChannel? = null) {
        viewModelScope.launch { signOut(channel) }
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
