/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.data.network

import com.gy.githubclient.core.domain.network.ApiChannel
import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.core.domain.network.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 统一网络客户端测试：token 注入、错误映射、401 回调、ETag 条件请求。 */
class GitHubApiClientTest {
    @Test
    fun injectsTokenAndVersionHeader() =
        runTest {
            var seenAuth: String? = null
            var seenVersion: String? = null
            val engine =
                MockEngine { request ->
                    seenAuth = request.headers[HttpHeaders.Authorization]
                    seenVersion = request.headers["X-GitHub-Api-Version"]
                    respond("{}", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val client = buildClient(engine, FakeTokenProvider(primary = "ghp_test"))

            val result = client.request(path = "/user") { "ok" }

            assertTrue(result is NetworkResult.Success)
            assertEquals("Bearer ghp_test", seenAuth)
            assertEquals("2026-03-10", seenVersion)
        }

    @Test
    fun missingTokenReturnsUnauthorizedWithoutRequest() =
        runTest {
            var requested = false
            val engine =
                MockEngine {
                    requested = true
                    respond("{}")
                }
            val client = buildClient(engine, FakeTokenProvider(primary = null))

            val result = client.request(path = "/user") { "ok" }

            assertTrue(result is NetworkResult.Failure)
            assertEquals(ApiError.Unauthorized, (result as NetworkResult.Failure).error)
            assertTrue(!requested)
        }

    @Test
    fun unauthorizedResponseTriggersCallback() =
        runTest {
            val provider = FakeTokenProvider(primary = "expired")
            val engine =
                MockEngine {
                    respond("{}", HttpStatusCode.Unauthorized)
                }
            val client = buildClient(engine, provider)

            val result = client.request(path = "/user") { "ok" }

            assertEquals(ApiError.Unauthorized, (result as NetworkResult.Failure).error)
            assertEquals(ApiChannel.PRIMARY, provider.unauthorizedChannel)
        }

    @Test
    fun notFoundAndRateLimitMapCorrectly() =
        runTest {
            val engine404 =
                MockEngine { respond("{}", HttpStatusCode.NotFound) }
            val result404 = buildClient(engine404, FakeTokenProvider("t")).request(path = "/x") { "ok" }
            assertEquals(ApiError.NotFound, (result404 as NetworkResult.Failure).error)

            val engineRate =
                MockEngine {
                    respond(
                        "{}",
                        HttpStatusCode.Forbidden,
                        headersOf("X-RateLimit-Remaining", "0"),
                    )
                }
            val resultRate =
                buildClient(engineRate, FakeTokenProvider("t"), maxRetries = 0)
                    .request(path = "/x") { "ok" }
            assertTrue((resultRate as NetworkResult.Failure).error is ApiError.RateLimited)
        }

    @Test
    fun notModifiedReturnsConditionalNotModified() =
        runTest {
            val engine =
                MockEngine { respond("", HttpStatusCode.NotModified) }
            val client = buildClient(engine, FakeTokenProvider("t"))

            val result =
                client.requestConditional(path = "/repos/o/r", etag = "W/\"abc\"") { "data" }

            assertTrue(result is NetworkResult.Success)
            assertEquals(
                com.gy.githubclient.core.domain.network.Conditional.NotModified,
                (result as NetworkResult.Success).data,
            )
        }

    private fun buildClient(
        engine: MockEngine,
        provider: AuthTokenProvider,
        maxRetries: Int = 0,
    ): GitHubApiClient =
        GitHubApiClient(
            HttpClient(engine) { expectSuccess = false },
            provider,
            maxRetries,
        )

    private class FakeTokenProvider(
        private val primary: String?,
    ) : AuthTokenProvider {
        var unauthorizedChannel: ApiChannel? = null
            private set

        override suspend fun tokenFor(channel: ApiChannel): String? =
            if (channel == ApiChannel.PRIMARY) primary else null

        override suspend fun onUnauthorized(channel: ApiChannel) {
            unauthorizedChannel = channel
        }
    }
}
