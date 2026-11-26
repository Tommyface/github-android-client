/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.data.network

import com.gy.githubclient.core.domain.network.ApiChannel
import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.core.domain.network.Conditional
import com.gy.githubclient.core.domain.network.NetworkResult
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import kotlin.math.min
import kotlin.random.Random
import kotlinx.coroutines.delay

/**
 * 全 App 共用的统一 REST 客户端封装：
 * - 自动注入 Bearer token（按 [ApiChannel] 取主/通知通道）与统一版本头。
 * - 401 → 回调 [AuthTokenProvider.onUnauthorized] 并映射 [ApiError.Unauthorized]。
 * - 403/429 限流 → 读取 `Retry-After` / `X-RateLimit-Reset` 退避重试；
 *   退避采用**指数 + 抖动**，避免多客户端同步重试形成尖峰。
 * - 二级限流（secondary rate limit）→ 更长的冷却时间。
 * - 网络异常 → [ApiError.Network]；其余状态 → [ApiErrorMapper]。
 * - [requestAllPages] 支持 `Link` 头分页，避免"只有首屏"的数据截断。
 *
 * token 明文不进入本类的日志或领域模型。
 */
class GitHubApiClient(
    private val httpClient: HttpClient,
    private val tokenProvider: AuthTokenProvider,
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
    private val random: Random = Random.Default,
) {
    /** 普通请求：把成功响应交给 [mapper] 解析。 */
    suspend fun <T> request(
        method: HttpMethod = HttpMethod.Get,
        path: String,
        channel: ApiChannel = ApiChannel.PRIMARY,
        etag: String? = null,
        mapper: suspend (HttpResponse) -> T,
    ): NetworkResult<T> =
        requestInternal(
            spec = RequestSpec(method, path, channel, etag, mapper),
            attempt = 0,
        )

    /** 条件请求：返回新数据或 304 未修改。 */
    suspend fun <T> requestConditional(
        path: String,
        channel: ApiChannel = ApiChannel.PRIMARY,
        etag: String?,
        mapper: suspend (HttpResponse) -> T,
    ): NetworkResult<Conditional<T>> =
        requestInternal<Conditional<T>>(
            spec =
                RequestSpec(
                    method = HttpMethod.Get,
                    path = path,
                    channel = channel,
                    etag = etag,
                    mapper = { response ->
                        if (response.status.value == NOT_MODIFIED) {
                            Conditional.NotModified
                        } else {
                            Conditional.Data(mapper(response))
                        }
                    },
                ),
            attempt = 0,
        )

    /**
     * 分页请求：跟随 `Link` 头的 `rel="next"` 逐页取回并合并（M1-09）。
     *
     * - 单页失败即整体失败（不做"部分成功"静默截断，避免用户误以为已看完）；
     * - 最多 [maxPages] 页，防止异常分页导致无限拉取；
     * - 返回的 list 为各页顺序拼接。
     */
    suspend fun <T> requestAllPages(
        path: String,
        channel: ApiChannel = ApiChannel.PRIMARY,
        maxPages: Int = DEFAULT_MAX_PAGES,
        mapper: suspend (HttpResponse) -> T,
    ): NetworkResult<List<T>> {
        val collected = mutableListOf<T>()
        var nextPath: String? = path
        var pages = 0
        while (nextPath != null && pages < maxPages) {
            var nextUrl: String? = null
            val result =
                request(
                    path = nextPath,
                    channel = channel,
                ) { response ->
                    nextUrl = LinkHeader.next(response.headers[HttpHeaders.Link])
                    mapper(response)
                }
            when (result) {
                is NetworkResult.Failure -> return NetworkResult.Failure(result.error)
                is NetworkResult.Success -> collected += result.data
            }
            nextPath = nextUrl?.let { toRelativePath(it) }
            pages++
        }
        return NetworkResult.Success(collected)
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> requestInternal(
        spec: RequestSpec<T>,
        attempt: Int,
    ): NetworkResult<T> {
        val token = tokenProvider.tokenFor(spec.channel)
        if (token == null) {
            return NetworkResult.Failure(ApiError.Unauthorized)
        }
        return try {
            val response: HttpResponse =
                httpClient.request(BASE_URL + spec.path) {
                    this.method = spec.method
                    applyHeaders(this, token, spec.etag)
                }
            when {
                response.status.isSuccess() || response.status.value == NOT_MODIFIED ->
                    NetworkResult.Success(spec.mapper(response))

                response.status.value == UNAUTHORIZED -> {
                    tokenProvider.onUnauthorized(spec.channel)
                    NetworkResult.Failure(ApiError.Unauthorized)
                }

                response.status.value == FORBIDDEN ||
                    response.status.value == TOO_MANY_REQUESTS -> {
                    val error = ApiErrorMapper.fromResponse(response)
                    if (error is ApiError.RateLimited && attempt < maxRetries) {
                        delay(retryDelayMillis(error, response, attempt))
                        requestInternal(spec, attempt + 1)
                    } else {
                        NetworkResult.Failure(error)
                    }
                }

                else -> NetworkResult.Failure(ApiErrorMapper.fromResponse(response))
            }
        } catch (e: Exception) {
            // 网络层统一兜底：Ktor 可能抛多种 IOException / SerializationException。
            NetworkResult.Failure(ApiErrorMapper.fromException(e))
        }
    }

    /**
     * 计算退避时长（毫秒）：
     * 1. 服务端给了等待时间（`Retry-After` / `X-RateLimit-Reset`）→ 以它为准（上限 [MAX_BACKOFF_MILLIS]）；
     * 2. 二级限流 → 强制冷却 [SECONDARY_LIMIT_COOLDOWN_MILLIS]；
     * 3. 都没有 → 指数退避（2^attempt）并叠加 ±20% 抖动，避免同步重试尖峰。
     */
    internal suspend fun retryDelayMillis(
        error: ApiError.RateLimited,
        response: HttpResponse,
        attempt: Int,
    ): Long {
        val serverWait = error.retryAfterSeconds?.times(MILLIS_PER_SECOND)
        if (isSecondaryRateLimited(response)) {
            return SECONDARY_LIMIT_COOLDOWN_MILLIS
        }
        if (serverWait != null) {
            return serverWait.coerceIn(0L, MAX_BACKOFF_MILLIS)
        }
        val exponential = BASE_BACKOFF_MILLIS shl attempt.coerceAtMost(MAX_SHIFT)
        val jittered = exponential * (1.0 - JITTER_RATIO + random.nextDouble() * 2 * JITTER_RATIO)
        return min(jittered.toLong(), MAX_BACKOFF_MILLIS)
    }

    /** 请求参数封装：降低内部方法参数数量。 */
    private data class RequestSpec<T>(
        val method: HttpMethod,
        val path: String,
        val channel: ApiChannel,
        val etag: String?,
        val mapper: suspend (HttpResponse) -> T,
    )

    private fun applyHeaders(
        builder: HttpRequestBuilder,
        token: String,
        etag: String?,
    ) {
        builder.header(HttpHeaders.Authorization, "Bearer $token")
        builder.header(HttpHeaders.Accept, "application/vnd.github+json")
        builder.header(API_VERSION_HEADER, API_VERSION)
        if (etag != null) builder.header(HttpHeaders.IfNoneMatch, etag)
    }

    /** 绝对 URL → 相对路径（客户端统一拼 BASE_URL）。 */
    private fun toRelativePath(url: String): String =
        if (url.startsWith(BASE_URL)) url.removePrefix(BASE_URL) else url

    private companion object {
        const val BASE_URL = "https://api.github.com"
        const val API_VERSION_HEADER = "X-GitHub-Api-Version"
        const val API_VERSION = "2026-03-10"
        const val NOT_MODIFIED = 304
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val TOO_MANY_REQUESTS = 429
        const val DEFAULT_MAX_RETRIES = 2
        const val DEFAULT_MAX_PAGES = 5
        const val MILLIS_PER_SECOND = 1000L
        const val BASE_BACKOFF_MILLIS = 1_000L
        const val MAX_BACKOFF_MILLIS = 60_000L
        const val SECONDARY_LIMIT_COOLDOWN_MILLIS = 60_000L
        const val MAX_SHIFT = 6
        const val JITTER_RATIO = 0.2
    }
}

/**
 * 是否为 GitHub 的**二级限流**（secondary rate limit）。
 *
 * 与"主额度耗尽"不同：主额度耗尽看 `X-RateLimit-Remaining == 0`；
 * 二级限流返回 403 且响应体含 `secondary rate limit` 字样，需要更长冷却。
 */
internal suspend fun isSecondaryRateLimited(response: HttpResponse): Boolean {
    if (response.status.value != 403) return false
    val text = runCatching { response.bodyAsText() }.getOrNull().orEmpty()
    return text.contains("secondary rate limit", ignoreCase = true) ||
        text.contains("abuse detection", ignoreCase = true)
}
