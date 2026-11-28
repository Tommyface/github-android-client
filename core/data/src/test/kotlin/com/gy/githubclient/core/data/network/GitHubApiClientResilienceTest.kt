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
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * M1-11：统一网络客户端的**错误分支与韧性**覆盖。
 *
 * 这里是全项目风险的集中点——所有业务请求都从这里出去，
 * 因此「错误怎么被判定、退避怎么算、分页怎么停」必须有穷举测试。
 */
class GitHubApiClientResilienceTest {
    // ── 退避策略 ────────────────────────────────────────────────────

    @Test
    fun retryDelayPrefersRetryAfterHeader() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        "{}",
                        HttpStatusCode.TooManyRequests,
                        headersOf(HttpHeaders.RetryAfter, "7"),
                    )
                }
            val client = buildClient(engine)
            val response = engineResponse(engine)

            val delay =
                client.retryDelayMillis(
                    ApiError.RateLimited(retryAfterSeconds = 7),
                    response,
                    attempt = 0,
                )
            assertEquals(7_000L, delay)
        }

    @Test
    fun retryDelayFallsBackToExponentialWithJitter() =
        runTest {
            val engine = MockEngine { respond("{}", HttpStatusCode.TooManyRequests) }
            val client = buildClient(engine)
            val response = engineResponse(engine)

            val delay =
                client.retryDelayMillis(
                    ApiError.RateLimited(retryAfterSeconds = null),
                    response,
                    attempt = 0,
                )
            // 基础退避 1000ms，抖动 ±20% → [800, 1200]
            assertTrue("退避应落在 800~1200ms，实际 $delay", delay in 800L..1200L)
        }

    @Test
    fun retryDelayGrowsExponentially() =
        runTest {
            val engine = MockEngine { respond("{}", HttpStatusCode.TooManyRequests) }
            val client = buildClient(engine)
            val response = engineResponse(engine)

            val first =
                client.retryDelayMillis(
                    ApiError.RateLimited(retryAfterSeconds = null),
                    response,
                    attempt = 0,
                )
            val second =
                client.retryDelayMillis(
                    ApiError.RateLimited(retryAfterSeconds = null),
                    response,
                    attempt = 1,
                )
            assertTrue("第 2 次退避应大于第 1 次（$first → $second）", second > first)
        }

    @Test
    fun secondaryRateLimitGetsLongCooldown() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        """{"message":"You have exceeded a secondary rate limit"}""",
                        HttpStatusCode.Forbidden,
                    )
                }
            val client = buildClient(engine)
            val response = engineResponse(engine)

            val delay =
                client.retryDelayMillis(
                    ApiError.RateLimited(retryAfterSeconds = null),
                    response,
                    attempt = 0,
                )
            assertEquals(60_000L, delay)
        }

    // ── 错误映射 ────────────────────────────────────────────────────

    @Test
    fun serverErrorMapsToHttpError() =
        runTest {
            val engine = MockEngine { respond("{}", HttpStatusCode.InternalServerError) }
            val result = buildClient(engine).request(path = "/x") { "ok" }
            val error = (result as NetworkResult.Failure).error
            assertTrue("应为 Http 错误，实际 $error", error is ApiError.Http)
        }

    @Test
    fun mapperExceptionIsMappedToNetworkError() =
        runTest {
            val engine = MockEngine { respond("not-json", HttpStatusCode.OK) }
            val result =
                buildClient(engine).request(path = "/x") { response ->
                    error("解析失败")
                }
            val error = (result as NetworkResult.Failure).error
            assertTrue("异常应被映射为 Network 错误，实际 $error", error is ApiError.Network)
        }

    @Test
    fun rateLimitedResponseRetriesUpToLimit() =
        runTest {
            var attempts = 0
            val engine =
                MockEngine {
                    attempts++
                    respond("{}", HttpStatusCode.TooManyRequests)
                }
            val client = buildClient(engine, maxRetries = 2)

            val result = client.request(path = "/x") { "ok" }

            assertTrue((result as NetworkResult.Failure).error is ApiError.RateLimited)
            assertEquals("初次 + 2 次重试 = 3 次", 3, attempts)
        }

    // ── 分页 ────────────────────────────────────────────────────────

    @Test
    fun requestAllPagesFollowsLinkHeader() =
        runTest {
            var page = 0
            val engine =
                MockEngine {
                    page++
                    if (page == 1) {
                        respond(
                            """{"n":1}""",
                            HttpStatusCode.OK,
                            headersOf(
                                HttpHeaders.Link,
                                """<https://api.github.com/items?page=2>; rel="next"""",
                            ),
                        )
                    } else {
                        respond("""{"n":2}""", HttpStatusCode.OK)
                    }
                }
            val client = buildClient(engine)

            val result =
                client.requestAllPages(path = "/items") { response ->
                    // 一页一个元素：requestAllPages 现在收「一页的元素列表」，内部扁平拼接。
                    listOf(response.bodyAsString())
                }

            assertTrue(result is NetworkResult.Success)
            val page = (result as NetworkResult.Success).data
            assertEquals(2, page.items.size)
            assertEquals("""{"n":1}""", page.items[0])
            assertEquals("""{"n":2}""", page.items[1])
            assertEquals("Link 头没有 next 时不应标截断", false, page.truncated)
        }

    @Test
    fun requestAllPagesStopsWhenPageFails() =
        runTest {
            var page = 0
            val engine =
                MockEngine {
                    page++
                    if (page == 1) {
                        respond(
                            """{"n":1}""",
                            HttpStatusCode.OK,
                            headersOf(
                                HttpHeaders.Link,
                                """<https://api.github.com/items?page=2>; rel="next"""",
                            ),
                        )
                    } else {
                        respond("{}", HttpStatusCode.InternalServerError)
                    }
                }
            val client = buildClient(engine)

            val result = client.requestAllPages(path = "/items") { listOf(it.bodyAsString()) }

            assertTrue("单页失败应整体失败，而不是静默截断", result is NetworkResult.Failure)
        }

    @Test
    fun requestAllPagesRespectsMaxPages() =
        runTest {
            var page = 0
            val engine =
                MockEngine {
                    page++
                    respond(
                        """{"n":$page}""",
                        HttpStatusCode.OK,
                        headersOf(
                            HttpHeaders.Link,
                            """<https://api.github.com/items?page=${page + 1}>; rel="next"""",
                        ),
                    )
                }
            val client = buildClient(engine)

            val result =
                client.requestAllPages(path = "/items", maxPages = 3) { listOf(it.bodyAsString()) }

            val pageResult = (result as NetworkResult.Success).data
            assertEquals("最多取 3 页", 3, pageResult.items.size)
            assertEquals(3, page)
            // 第 3 页之后仍有 next → 必须标截断，否则调用方会以为"这就是全部"。
            assertTrue("达到 maxPages 且仍有下一页时必须标 truncated", pageResult.truncated)
            assertNotNull(pageResult.nextPath)
        }

    @Test
    fun requestAllPagesReportsTruncationAtDefaultMaxPages() =
        runTest {
            // 规范里的验收口径：6 页 × 100 条，默认上限 DEFAULT_MAX_PAGES = 5
            // → items 500 条 + truncated=true。修复前这个方法返回普通 List，
            // 调用方完全拿不到"还差一页"这个事实。
            var page = 0
            val engine =
                MockEngine {
                    page++
                    respond(
                        "[]",
                        HttpStatusCode.OK,
                        headersOf(
                            HttpHeaders.Link,
                            """<https://api.github.com/items?page=${page + 1}>; rel="next"""",
                        ),
                    )
                }
            val client = buildClient(engine)

            val result =
                client.requestAllPages(path = "/items") { List(100) { index -> "item-$index" } }

            val pageResult = (result as NetworkResult.Success).data
            assertEquals("5 页 × 100 条 = 500", 500, pageResult.items.size)
            assertEquals("第 5 页后就该停（DEFAULT_MAX_PAGES）", 5, page)
            assertTrue(pageResult.truncated)
            assertTrue(
                "nextPath 应指向未取的第 6 页",
                pageResult.nextPath?.contains("page=6") == true,
            )
        }

    // ── 辅助 ────────────────────────────────────────────────────────

    private fun buildClient(
        engine: MockEngine,
        maxRetries: Int = 0,
    ): GitHubApiClient =
        GitHubApiClient(
            httpClient = HttpClient(engine) { expectSuccess = false },
            tokenProvider = FakeTokenProvider(),
            maxRetries = maxRetries,
        )

    /** 取一个真实响应对象，用于直接验证退避计算（由 engine 的 handler 决定状态与头）。 */
    private suspend fun engineResponse(engine: MockEngine): HttpResponse {
        val client = HttpClient(engine) { expectSuccess = false }
        return client.get("https://api.github.com/test") {
            headers.append(HttpHeaders.Accept, "application/json")
        }
    }

    private class FakeTokenProvider : AuthTokenProvider {
        override suspend fun tokenFor(channel: ApiChannel): String? = "ghp_test"

        override suspend fun onUnauthorized(channel: ApiChannel) = Unit
    }
}

/** 读取响应体文本（测试辅助）。 */
private suspend fun HttpResponse.bodyAsString(): String = bodyAsText()
