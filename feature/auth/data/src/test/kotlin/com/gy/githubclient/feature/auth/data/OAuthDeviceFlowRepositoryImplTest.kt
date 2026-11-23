/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.data

import com.gy.githubclient.feature.auth.data.remote.GitHubOAuthApi
import com.gy.githubclient.feature.auth.domain.model.DeviceFlowPollResult
import com.gy.githubclient.feature.auth.domain.model.DeviceFlowStartResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 回归：API 结果 → 领域模型映射（Pending/SlowDown/Expired/Denied/成功/网络错误）。 */
class OAuthDeviceFlowRepositoryImplTest {
    private lateinit var server: MockWebServer
    private lateinit var repository: OAuthDeviceFlowRepositoryImpl

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        val client =
            HttpClient(OkHttp) {
                install(HttpTimeout) {
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 30_000
                    requestTimeoutMillis = 30_000
                }
            }
        repository =
            OAuthDeviceFlowRepositoryImpl(
                api = GitHubOAuthApi(client, baseUrl = server.url("/").toString().removeSuffix("/")),
                clientId = "test-client",
            )
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun json(body: String): MockResponse =
        MockResponse()
            .setBody(body)
            .setHeader("Content-Type", "application/json;charset=utf-8")

    @Test
    fun startMapsDtoToChallenge() =
        runBlocking {
            server.enqueue(
                json(
                    """{"device_code":"dev-1","user_code":"WXYZ-9999",""" +
                        """"verification_uri":"https://github.com/login/device",""" +
                        """"expires_in":600,"interval":5}""",
                ),
            )
            val result = repository.startDeviceFlow()
            assertTrue(result is DeviceFlowStartResult.Success)
            val challenge = (result as DeviceFlowStartResult.Success).challenge
            assertEquals("dev-1", challenge.deviceCode)
            assertEquals("WXYZ-9999", challenge.userCode)
            assertEquals(600_000L, challenge.expiresInMillis)
            assertEquals(5, challenge.intervalSeconds)
        }

    @Test
    fun pollMapsErrorStates() =
        runBlocking {
            val cases =
                listOf(
                    "authorization_pending" to DeviceFlowPollResult.Pending,
                    "slow_down" to DeviceFlowPollResult.SlowDown,
                    "expired_token" to DeviceFlowPollResult.Expired,
                    "access_denied" to DeviceFlowPollResult.Denied,
                )
            for ((error, expected) in cases) {
                server.enqueue(json("""{"error":"$error"}"""))
                val result = repository.pollForToken("dev-1")
                assertEquals("$error 应映射为 $expected", expected, result)
            }
        }

    @Test
    fun pollUnknownErrorMapsToNetworkError() =
        runBlocking {
            server.enqueue(json("""{"error":"device_flow_disabled","error_description":"off"}"""))
            val result = repository.pollForToken("dev-1")
            assertTrue(result is DeviceFlowPollResult.NetworkError)
            assertTrue((result as DeviceFlowPollResult.NetworkError).message.contains("device_flow_disabled"))
        }

    @Test
    fun pollMissingTokenAndErrorMapsToNetworkError() =
        runBlocking {
            server.enqueue(json("""{"token_type":"bearer"}"""))
            val result = repository.pollForToken("dev-1")
            assertTrue(result is DeviceFlowPollResult.NetworkError)
        }

    @Test
    fun pollHttpErrorMapsToNetworkError() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(500).setBody("boom"))
            val result = repository.pollForToken("dev-1")
            assertTrue(result is DeviceFlowPollResult.NetworkError)
        }

    @Test
    fun pollAuthorizedMapsToToken() =
        runBlocking {
            server.enqueue(json("""{"access_token":"gho_ok","token_type":"bearer","scope":"repo"}"""))
            val result = repository.pollForToken("dev-1")
            assertTrue(result is DeviceFlowPollResult.Authorized)
            assertEquals("gho_ok", (result as DeviceFlowPollResult.Authorized).accessToken)
        }
}
