/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.domain

import com.gy.githubclient.feature.auth.domain.model.DeviceCodeChallenge
import com.gy.githubclient.feature.auth.domain.model.DeviceFlowPollResult
import com.gy.githubclient.feature.auth.domain.model.DeviceFlowStartResult
import com.gy.githubclient.feature.auth.domain.repository.OAuthDeviceFlowRepository
import com.gy.githubclient.feature.auth.domain.usecase.PollDeviceTokenUseCase
import com.gy.githubclient.feature.auth.domain.usecase.StartDeviceFlowUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Device Flow 用例透传测试：用例层不加工结果，仅委托仓库。 */
class DeviceFlowUseCasesTest {
    private class RecordingRepository : OAuthDeviceFlowRepository {
        private val challenge =
            DeviceCodeChallenge(
                deviceCode = "dev-1",
                userCode = "AAAA-BBBB",
                verificationUri = "https://github.com/login/device",
                expiresInMillis = 900_000L,
                intervalSeconds = 5,
            )

        var startCalls = 0
        var pollCalls = 0
        var lastPolledCode: String? = null

        override suspend fun startDeviceFlow(): DeviceFlowStartResult =
            DeviceFlowStartResult.Success(challenge).also { startCalls++ }

        override suspend fun pollForToken(deviceCode: String): DeviceFlowPollResult =
            DeviceFlowPollResult.Pending.also {
                pollCalls++
                lastPolledCode = deviceCode
            }
    }

    @Test
    fun startUseCaseDelegatesToRepository() =
        runTest {
            val repo = RecordingRepository()
            val result = StartDeviceFlowUseCase(repo)()
            assertTrue(result is DeviceFlowStartResult.Success)
            assertEquals("dev-1", (result as DeviceFlowStartResult.Success).challenge.deviceCode)
            assertEquals(1, repo.startCalls)
        }

    @Test
    fun pollUseCaseDelegatesDeviceCode() =
        runTest {
            val repo = RecordingRepository()
            val result = PollDeviceTokenUseCase(repo)("dev-42")
            assertEquals(DeviceFlowPollResult.Pending, result)
            assertEquals("dev-42", repo.lastPolledCode)
        }
}
