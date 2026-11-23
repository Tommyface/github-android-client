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
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay

/**
 * 全 App 共用的统一 REST 客户端封装：
 * - 自动注入 Bearer token（按 [ApiChannel] 取主/通知通道）与统一版本头。
 * - 401 → 回调 [AuthTokenProvider.onUnauthorized] 并映射 [ApiError.Unauthorized]。
 * - 403/429 限流 → 读取 X-RateLimit-Reset 有限退避重试。
 * - 网络异常 → [ApiError.Network]；其余状态 → [ApiErrorMapper]。
 *
 * token 明文不进入本类的日志或领域模型。
 */
class GitHubApiClient(
    private val httpClient: HttpClient,
    private val tokenProvider: AuthTokenProvider,
    private val maxRetries: Int = DEFAULT_MAX_RETRIES,
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
                        val wait =
                            (error.retryAfterSeconds ?: DEFAULT_BACKOFF_SECONDS)
                                .coerceAtMost(MAX_BACKOFF_SECONDS)
                        delay(wait * MILLIS_PER_SECOND)
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

    private companion object {
        const val BASE_URL = "https://api.github.com"
        const val API_VERSION_HEADER = "X-GitHub-Api-Version"
        const val API_VERSION = "2026-03-10"
        const val NOT_MODIFIED = 304
        const val UNAUTHORIZED = 401
        const val FORBIDDEN = 403
        const val TOO_MANY_REQUESTS = 429
        const val DEFAULT_MAX_RETRIES = 2
        const val DEFAULT_BACKOFF_SECONDS = 5L
        const val MAX_BACKOFF_SECONDS = 60L
        const val MILLIS_PER_SECOND = 1000L
    }
}
