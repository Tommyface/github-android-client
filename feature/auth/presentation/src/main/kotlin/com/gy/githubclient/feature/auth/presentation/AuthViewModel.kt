/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.feature.auth.domain.model.AuthResult
import com.gy.githubclient.feature.auth.domain.model.AuthState
import com.gy.githubclient.feature.auth.domain.model.DeviceCodeChallenge
import com.gy.githubclient.feature.auth.domain.model.DeviceFlowPollResult
import com.gy.githubclient.feature.auth.domain.model.DeviceFlowStartResult
import com.gy.githubclient.feature.auth.domain.model.TokenChannel
import com.gy.githubclient.feature.auth.domain.usecase.BindNotificationTokenUseCase
import com.gy.githubclient.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.gy.githubclient.feature.auth.domain.usecase.PollDeviceTokenUseCase
import com.gy.githubclient.feature.auth.domain.usecase.RestoreSessionUseCase
import com.gy.githubclient.feature.auth.domain.usecase.SignInWithOAuthUseCase
import com.gy.githubclient.feature.auth.domain.usecase.SignInWithPatUseCase
import com.gy.githubclient.feature.auth.domain.usecase.SignOutUseCase
import com.gy.githubclient.feature.auth.domain.usecase.StartDeviceFlowUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

/** 登录表单与一次性事件。 */
data class AuthUiState(
    val primaryToken: String = "",
    val notificationToken: String = "",
    val isSubmitting: Boolean = false,
    val isBindingNotification: Boolean = false,
    val deviceFlow: DeviceFlowUiState = DeviceFlowUiState.Idle,
)

/** OAuth Device Flow 界面状态机。 */
sealed interface DeviceFlowUiState {
    data object Idle : DeviceFlowUiState

    data object Requesting : DeviceFlowUiState

    data class AwaitingAuthorization(
        val userCode: String,
        val verificationUri: String,
        val expiresAtMillis: Long,
    ) : DeviceFlowUiState

    data object Expired : DeviceFlowUiState

    data object Denied : DeviceFlowUiState
}

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
 * - Device Flow：发起授权 → 展示 user_code → 按间隔轮询 → 复用 OAuth 登录管线入库。
 */
@Suppress("LongParameterList") // 用例逐一显式注入（Koin 构造式 DI），超出阈值属预期
class AuthViewModel(
    private val signInWithPat: SignInWithPatUseCase,
    private val signInWithOAuth: SignInWithOAuthUseCase,
    private val bindNotificationToken: BindNotificationTokenUseCase,
    private val startDeviceFlow: StartDeviceFlowUseCase,
    private val pollDeviceToken: PollDeviceTokenUseCase,
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

    private var deviceFlowJob: Job? = null

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

    /**
     * 发起 OAuth Device Flow：请求 user_code 并进入轮询。
     * 注意：方法名不可与构造参数 [startDeviceFlow] 同名，否则递归自调用导致状态卡死。
     */
    fun beginDeviceFlow() {
        if (deviceFlowJob?.isActive == true) return
        deviceFlowJob =
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(deviceFlow = DeviceFlowUiState.Requesting)
                when (val result = startDeviceFlow()) {
                    is DeviceFlowStartResult.Error -> {
                        _uiState.value = _uiState.value.copy(deviceFlow = DeviceFlowUiState.Idle)
                        _events.trySend(
                            AuthEvent.ShowMessage("发起授权失败：${friendlyDeviceFlowError(result.message)}"),
                        )
                    }

                    is DeviceFlowStartResult.Success -> {
                        val challenge = result.challenge
                        _uiState.value =
                            _uiState.value.copy(
                                deviceFlow =
                                    DeviceFlowUiState.AwaitingAuthorization(
                                        userCode = challenge.userCode,
                                        verificationUri = challenge.verificationUri,
                                        expiresAtMillis =
                                            System.currentTimeMillis() + challenge.expiresInMillis,
                                    ),
                            )
                        runPollLoop(challenge)
                    }
                }
            }
    }

    /** 取消 Device Flow（轮询停止，界面复位）。 */
    fun cancelDeviceFlow() {
        deviceFlowJob?.cancel()
        deviceFlowJob = null
        _uiState.value = _uiState.value.copy(deviceFlow = DeviceFlowUiState.Idle)
    }

    /**
     * 轮询主循环：按 GitHub 建议间隔请求令牌端点。
     * - Pending：静默继续；SlowDown：间隔 +5 秒。
     * - 单次网络错误不中断（直连 github.com 不稳），连续超限才中止。
     * - deadline 由 device_code 的 expires_in 决定，到期置 Expired。
     */
    private suspend fun runPollLoop(challenge: DeviceCodeChallenge) {
        var interval = challenge.intervalSeconds.coerceAtLeast(MIN_INTERVAL_SECONDS)
        var consecutiveErrors = 0
        val deadline = System.currentTimeMillis() + challenge.expiresInMillis
        while (System.currentTimeMillis() < deadline && coroutineContext.isActive) {
            delay(interval * MILLIS_PER_SECOND)
            when (val result = pollDeviceToken(challenge.deviceCode)) {
                is DeviceFlowPollResult.Authorized -> {
                    completeOAuthSignIn(result.accessToken, result.scopes)
                    return
                }

                DeviceFlowPollResult.Pending -> consecutiveErrors = 0
                DeviceFlowPollResult.SlowDown -> {
                    interval += SLOW_DOWN_EXTRA_SECONDS
                    consecutiveErrors = 0
                }

                DeviceFlowPollResult.Expired -> {
                    _uiState.value = _uiState.value.copy(deviceFlow = DeviceFlowUiState.Expired)
                    _events.trySend(AuthEvent.ShowMessage("授权码已过期，请重新发起"))
                    return
                }

                DeviceFlowPollResult.Denied -> {
                    _uiState.value = _uiState.value.copy(deviceFlow = DeviceFlowUiState.Denied)
                    _events.trySend(AuthEvent.ShowMessage("你拒绝了对本应用的授权"))
                    return
                }

                is DeviceFlowPollResult.NetworkError -> {
                    consecutiveErrors++
                    if (consecutiveErrors >= MAX_CONSECUTIVE_ERRORS) {
                        _uiState.value = _uiState.value.copy(deviceFlow = DeviceFlowUiState.Idle)
                        _events.trySend(
                            AuthEvent.ShowMessage(
                                "网络异常，授权已中止：${friendlyDeviceFlowError(result.message)}",
                            ),
                        )
                        return
                    }
                }
            }
        }
        _uiState.value = _uiState.value.copy(deviceFlow = DeviceFlowUiState.Expired)
        _events.trySend(AuthEvent.ShowMessage("授权码已过期，请重新发起"))
    }

    /** 拿到 access_token 后复用 OAuth 登录管线（/user 校验 + 安全存储 + 状态发布）。 */
    private suspend fun completeOAuthSignIn(
        accessToken: String,
        scopes: List<String> = emptyList(),
    ) {
        when (val result = signInWithOAuth(accessToken, scopes)) {
            is AuthResult.Success -> {
                _uiState.value = _uiState.value.copy(deviceFlow = DeviceFlowUiState.Idle)
                _events.trySend(AuthEvent.LoginSuccess)
            }

            is AuthResult.Failure.InvalidToken ->
                deviceFlowFailure("OAuth token 校验未通过")

            is AuthResult.Failure.RateLimited ->
                deviceFlowFailure("API 限流，请稍后再试")

            is AuthResult.Failure.Network ->
                deviceFlowFailure("网络错误：${result.cause}")

            is AuthResult.Failure.UnsupportedScope ->
                deviceFlowFailure("授权 scope 不足")
        }
    }

    private fun deviceFlowFailure(message: String) {
        _uiState.value = _uiState.value.copy(deviceFlow = DeviceFlowUiState.Idle)
        _events.trySend(AuthEvent.ShowMessage("登录失败：$message"))
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
        const val MILLIS_PER_SECOND = 1000L
        const val MIN_INTERVAL_SECONDS = 2
        const val SLOW_DOWN_EXTRA_SECONDS = 5
        const val MAX_CONSECUTIVE_ERRORS = 3
    }
}

/**
 * 把底层网络异常的英文原文翻译成用户可读的中文指引（顶层便于单测）。
 *
 * 国内直连 github.com 常见失败形态：Socket timeout / 连接重置 / DNS 解析失败；
 * Device Flow 端点无法走文件镜像加速，只能引导用户切换网络或开代理。
 */
internal fun friendlyDeviceFlowError(raw: String): String {
    val lower = raw.lowercase()
    return when {
        "timeout" in lower ->
            "网络连接超时：直连 github.com 无响应。请切换网络（WiFi ↔ 流量）、开启代理，" +
                "或稍后重试（github.com 在国内间歇可达）"

        listOf("unable to resolve", "unknownhost", "no address").any { it in lower } ->
            "域名解析失败：当前网络无法访问 github.com。请切换网络或开启代理后重试"

        listOf(
            "reset",
            "failed to connect",
            "unexpected end",
            "connection closed",
            "econnreset",
            "broken pipe",
        ).any { it in lower } ->
            "网络连接中断：请求被重置。请切换网络（WiFi ↔ 流量）或开启代理后重试"

        else -> raw
    }
}
