/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.data.network

import com.gy.githubclient.core.domain.network.ApiChannel
import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.core.mutation.MutationSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.CancellationException
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

    @Test
    fun readCancellationPropagatesInsteadOfBecomingNetworkError() =
        runTest {
            // 引擎直接抛 CancellationException：页面退出、用户取消、上游 withTimeout 都走这条路径。
            // 修复前 catch (Exception) 会把它吞成 NetworkResult.Failure(ApiError.Network)，
            // 于是「我取消了」在界面上显示成「网络错误」，还会触发一次无意义的重试提示。
            val engine = MockEngine { throw CancellationException("cancelled by test") }
            val client = buildClient(engine, FakeTokenProvider("t"))

            val thrown =
                runCatching { client.request(path = "/repos/o/r") { "data" } }.exceptionOrNull()

            assertTrue(
                "取消必须上抛 CancellationException，而不是被吞成 NetworkResult.Failure",
                thrown is CancellationException,
            )
        }

    @Test
    fun writeCancellationPropagatesInsteadOfBecomingWriteFailure() =
        runTest {
            // 写路径同理，而且后果更重：被吞成 MutationError 后 UI 会提示"操作失败"，
            // 用户重试一次已经发生的写（例如重复评论）。
            val engine = MockEngine { throw CancellationException("cancelled by test") }
            val client = buildClient(engine, FakeTokenProvider("t"))

            val thrown =
                runCatching {
                    client.mutate(
                        spec = MutationSpec(action = "star", target = "o/r"),
                        method = HttpMethod.Put,
                        path = "/user/starred/o/r",
                    ) { "ok" }
                }.exceptionOrNull()

            assertTrue(
                "写操作取消必须上抛，不能被吞成 MutationError",
                thrown is CancellationException,
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
