/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.presentation

import com.gy.githubclient.feature.auth.domain.model.AuthMethod
import com.gy.githubclient.feature.auth.domain.model.AuthResult
import com.gy.githubclient.feature.auth.domain.model.AuthSession
import com.gy.githubclient.feature.auth.domain.model.AuthState
import com.gy.githubclient.feature.auth.domain.model.AuthUser
import com.gy.githubclient.feature.auth.domain.model.DeviceCodeChallenge
import com.gy.githubclient.feature.auth.domain.model.DeviceFlowPollResult
import com.gy.githubclient.feature.auth.domain.model.DeviceFlowStartResult
import com.gy.githubclient.feature.auth.domain.model.TokenChannel
import com.gy.githubclient.feature.auth.domain.repository.AuthRepository
import com.gy.githubclient.feature.auth.domain.repository.OAuthDeviceFlowRepository
import com.gy.githubclient.feature.auth.domain.usecase.BindNotificationTokenUseCase
import com.gy.githubclient.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.gy.githubclient.feature.auth.domain.usecase.PollDeviceTokenUseCase
import com.gy.githubclient.feature.auth.domain.usecase.RestoreSessionUseCase
import com.gy.githubclient.feature.auth.domain.usecase.SignInWithOAuthUseCase
import com.gy.githubclient.feature.auth.domain.usecase.SignInWithPatUseCase
import com.gy.githubclient.feature.auth.domain.usecase.SignOutUseCase
import com.gy.githubclient.feature.auth.domain.usecase.StartDeviceFlowUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Device Flow 编排回归：
 * 发起 → 轮询（Pending/SlowDown/Authorized/Expired/Denied/网络错误）→ 复用 OAuth 登录管线。
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelDeviceFlowTest {
    private val dispatcher = StandardTestDispatcher()

    private class StubAuthRepository : AuthRepository {
        private val stubUser = AuthUser("gy", "Gy", null, "https://github.com/gy")
        val state = MutableStateFlow<AuthState>(AuthState.SignedOut)
        var oauthTokenReceived: String? = null
        var lastScopesReceived: List<String> = emptyList()
        var methodRecorded: AuthMethod? = null
        var oauthResult: AuthResult =
            AuthResult.Success(
                AuthSession(stubUser, true, false, AuthMethod.OAUTH),
            )

        override fun observeAuthState(): Flow<AuthState> = state

        override suspend fun currentSession(): AuthSession =
            AuthSession(stubUser, true, false, methodRecorded)

        override suspend fun restoreSession(): AuthState = AuthState.SignedOut

        override suspend fun signInWithPat(token: String): AuthResult = oauthResult

        override suspend fun signInWithOAuth(
            accessToken: String,
            scopes: List<String>,
        ): AuthResult {
            oauthTokenReceived = accessToken
            lastScopesReceived = scopes
            return oauthResult
        }

        override suspend fun bindNotificationToken(token: String): AuthResult = oauthResult

        override suspend fun getPrimaryToken(): String? = "token"

        override suspend fun getNotificationToken(): String? = null

        override suspend fun signOut(channel: TokenChannel?) = Unit

        override suspend fun setAuthMethod(method: AuthMethod) {
            methodRecorded = method
        }
    }

    private class FakeDeviceFlowRepository(
        private val startResult: DeviceFlowStartResult,
        private val pollResults: List<DeviceFlowPollResult>,
    ) : OAuthDeviceFlowRepository {
        var pollCalls = 0

        override suspend fun startDeviceFlow(): DeviceFlowStartResult = startResult

        override suspend fun pollForToken(deviceCode: String): DeviceFlowPollResult =
            pollResults.getOrElse(pollCalls) { DeviceFlowPollResult.Pending }.also { pollCalls++ }
    }

    private val successStart =
        DeviceFlowStartResult.Success(
            DeviceCodeChallenge(
                deviceCode = "dev-1",
                userCode = "ABCD-1234",
                verificationUri = "https://github.com/login/device",
                expiresInMillis = 900_000L,
                intervalSeconds = 2,
            ),
        )

    private fun buildViewModel(
        authRepo: StubAuthRepository,
        deviceRepo: OAuthDeviceFlowRepository,
    ): AuthViewModel =
        AuthViewModel(
            signInWithPat = SignInWithPatUseCase(authRepo),
            signInWithOAuth = SignInWithOAuthUseCase(authRepo),
            bindNotificationToken = BindNotificationTokenUseCase(authRepo),
            startDeviceFlow = StartDeviceFlowUseCase(deviceRepo),
            pollDeviceToken = PollDeviceTokenUseCase(deviceRepo),
            observeAuthState = ObserveAuthStateUseCase(authRepo),
            signOut = SignOutUseCase(authRepo),
            restoreSession = RestoreSessionUseCase(authRepo),
        )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun deviceFlowSuccessCompletesOAuthSignIn() =
        runTest(dispatcher) {
            val authRepo = StubAuthRepository()
            val deviceRepo =
                FakeDeviceFlowRepository(
                    successStart,
                    listOf(DeviceFlowPollResult.Authorized("gho_token", listOf("repo", "read:user"))),
                )
            val viewModel = buildViewModel(authRepo, deviceRepo)
            val events = mutableListOf<AuthEvent>()
            backgroundScope.launch { viewModel.events.collect { events.add(it) } }

            viewModel.beginDeviceFlow()
            advanceUntilIdle()

            assertEquals("gho_token", authRepo.oauthTokenReceived)
            assertEquals(AuthMethod.OAUTH, authRepo.methodRecorded)
            assertEquals(listOf("repo", "read:user"), authRepo.lastScopesReceived)
            assertTrue(events.contains(AuthEvent.LoginSuccess))
            assertEquals(DeviceFlowUiState.Idle, viewModel.uiState.value.deviceFlow)
        }

    @Test
    fun pendingThenAuthorizedCompletesSignIn() =
        runTest(dispatcher) {
            val authRepo = StubAuthRepository()
            val deviceRepo =
                FakeDeviceFlowRepository(
                    successStart,
                    listOf(
                        DeviceFlowPollResult.Pending,
                        DeviceFlowPollResult.SlowDown,
                        DeviceFlowPollResult.Authorized("gho_late"),
                    ),
                )
            val viewModel = buildViewModel(authRepo, deviceRepo)
            backgroundScope.launch { viewModel.events.collect { } }

            viewModel.beginDeviceFlow()
            advanceUntilIdle()

            assertEquals("gho_late", authRepo.oauthTokenReceived)
            assertEquals(3, deviceRepo.pollCalls)
        }

    @Test
    fun expiredPollSwitchesToExpiredState() =
        runTest(dispatcher) {
            val authRepo = StubAuthRepository()
            val deviceRepo =
                FakeDeviceFlowRepository(successStart, listOf(DeviceFlowPollResult.Expired))
            val viewModel = buildViewModel(authRepo, deviceRepo)
            backgroundScope.launch { viewModel.events.collect { } }

            viewModel.beginDeviceFlow()
            advanceUntilIdle()

            assertEquals(DeviceFlowUiState.Expired, viewModel.uiState.value.deviceFlow)
        }

    @Test
    fun deniedPollSwitchesToDeniedState() =
        runTest(dispatcher) {
            val authRepo = StubAuthRepository()
            val deviceRepo =
                FakeDeviceFlowRepository(successStart, listOf(DeviceFlowPollResult.Denied))
            val viewModel = buildViewModel(authRepo, deviceRepo)
            backgroundScope.launch { viewModel.events.collect { } }

            viewModel.beginDeviceFlow()
            advanceUntilIdle()

            assertEquals(DeviceFlowUiState.Denied, viewModel.uiState.value.deviceFlow)
        }

    @Test
    fun repeatedNetworkErrorsAbortFlow() =
        runTest(dispatcher) {
            val authRepo = StubAuthRepository()
            val deviceRepo =
                FakeDeviceFlowRepository(
                    successStart,
                    listOf(
                        DeviceFlowPollResult.NetworkError("timeout"),
                        DeviceFlowPollResult.NetworkError("timeout"),
                        DeviceFlowPollResult.NetworkError("timeout"),
                    ),
                )
            val viewModel = buildViewModel(authRepo, deviceRepo)
            backgroundScope.launch { viewModel.events.collect { } }

            viewModel.beginDeviceFlow()
            advanceUntilIdle()

            assertEquals(DeviceFlowUiState.Idle, viewModel.uiState.value.deviceFlow)
            assertEquals(null, authRepo.oauthTokenReceived)
        }

    @Test
    fun cancelResetsToIdle() =
        runTest(dispatcher) {
            val authRepo = StubAuthRepository()
            val deviceRepo =
                FakeDeviceFlowRepository(successStart, listOf(DeviceFlowPollResult.Pending))
            val viewModel = buildViewModel(authRepo, deviceRepo)
            backgroundScope.launch { viewModel.events.collect { } }

            viewModel.beginDeviceFlow()
            advanceTimeBy(2000)
            viewModel.cancelDeviceFlow()
            advanceUntilIdle()

            assertEquals(DeviceFlowUiState.Idle, viewModel.uiState.value.deviceFlow)
        }
}
