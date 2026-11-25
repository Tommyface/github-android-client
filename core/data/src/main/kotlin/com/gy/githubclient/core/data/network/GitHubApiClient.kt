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
import com.gy.githubclient.core.mutation.AuditSink
import com.gy.githubclient.core.mutation.FieldError
import com.gy.githubclient.core.mutation.GitHubErrorPayload
import com.gy.githubclient.core.mutation.MutationAttempt
import com.gy.githubclient.core.mutation.MutationError
import com.gy.githubclient.core.mutation.MutationErrorMapper
import com.gy.githubclient.core.mutation.MutationHeaders
import com.gy.githubclient.core.mutation.MutationResult
import com.gy.githubclient.core.mutation.MutationRetryPolicy
import com.gy.githubclient.core.mutation.MutationRunner
import com.gy.githubclient.core.mutation.MutationSpec
import com.gy.githubclient.core.mutation.NoOpAuditSink
import io.ktor.client.HttpClient
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable

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
    auditSink: AuditSink = NoOpAuditSink,
    mutationRetryPolicy: MutationRetryPolicy = MutationRetryPolicy(),
) {
    /** 写操作统一信封：错误分类、幂等重试与审计。 */
    private val mutationRunner =
        MutationRunner(
            retryPolicy = mutationRetryPolicy,
            auditSink = auditSink,
            sleeper = { delay(it) },
        )

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

    /**
     * 写请求：成功返回 [MutationResult.Success]，失败返回细粒度 [MutationError]。
     * 幂等操作在网络错误/5xx/限流时按 [MutationRetryPolicy] 自动重试；
     * 每次最终结果都会写入审计（成功与失败都留痕）。
     */
    @Suppress("TooGenericExceptionCaught", "LongParameterList")
    suspend fun <T> mutate(
        spec: MutationSpec,
        method: HttpMethod,
        path: String,
        body: String? = null,
        channel: ApiChannel = ApiChannel.PRIMARY,
        mapper: suspend (HttpResponse) -> T,
    ): MutationResult<T> {
        if (tokenProvider.tokenFor(channel) == null) {
            return mutationRunner.execute(spec) { MutationAttempt.Failure(MutationError.Unauthorized) }
        }
        return mutationRunner.execute(spec) {
            attemptMutation(method, path, body, channel, mapper)
        }
    }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun <T> attemptMutation(
        method: HttpMethod,
        path: String,
        body: String?,
        channel: ApiChannel,
        mapper: suspend (HttpResponse) -> T,
    ): MutationAttempt<T> {
        val token = tokenProvider.tokenFor(channel) ?: return MutationAttempt.Failure(MutationError.Unauthorized)
        return try {
            val response =
                httpClient.request(BASE_URL + path) {
                    this.method = method
                    applyHeaders(this, token, etag = null)
                    if (body != null) {
                        setBody(TextContent(body, ContentType.Application.Json))
                    }
                }
            val requestId = response.headers[HEADER_REQUEST_ID]
            if (response.status.isSuccess()) {
                MutationAttempt.Success(mapper(response), response.status.value, requestId)
            } else if (response.status.value == UNAUTHORIZED) {
                tokenProvider.onUnauthorized(channel)
                MutationAttempt.Failure(MutationError.Unauthorized, response.status.value, requestId)
            } else {
                mapMutationFailure(response, requestId)
            }
        } catch (e: Exception) {
            MutationAttempt.Failure(ApiErrorMapper.fromException(e).toMutationError())
        }
    }

    private suspend fun <T> mapMutationFailure(
        response: HttpResponse,
        requestId: String?,
    ): MutationAttempt<T> {
        val text = runCatching { response.bodyAsText() }.getOrDefault("")
        val payload = parseErrorPayload(text)
        val headers =
            MutationHeaders.fromRaw(
                response.headers.entries().associate { it.key to it.value.joinToString(",") },
            )
        val error = MutationErrorMapper.map(response.status.value, payload, headers)
        return MutationAttempt.Failure(error, response.status.value, requestId)
    }

    private fun parseErrorPayload(text: String): GitHubErrorPayload {
        if (text.isBlank()) return GitHubErrorPayload()
        return runCatching {
            val envelope = GitHubJson.decode(GitHubErrorEnvelope.serializer(), text)
            GitHubErrorPayload(
                message = envelope.message,
                errors =
                    envelope.errors.map {
                        FieldError(
                            field = it.field ?: it.resource.orEmpty(),
                            code = it.code.orEmpty(),
                            message = it.message,
                        )
                    },
            )
        }.getOrDefault(GitHubErrorPayload())
    }

    /** 普通读请求的 [ApiError] 转为写操作错误模型（供复用错误文案时使用）。 */
    private fun ApiError.toMutationError(): MutationError =
        when (this) {
            ApiError.Unauthorized -> MutationError.Unauthorized
            is ApiError.RateLimited -> MutationError.RateLimited(retryAfterSeconds)
            ApiError.NotFound -> MutationError.NotFound
            is ApiError.Network -> MutationError.Network(cause)
            is ApiError.Http -> MutationError.Unknown(code, message)
            is ApiError.Unknown -> MutationError.Unknown(message = message)
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
        const val HEADER_REQUEST_ID = "X-GitHub-Request-Id"
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

/** GitHub 错误响应外层结构（仅解析审计与表单错误所需的字段）。 */
@Serializable
private data class GitHubErrorEnvelope(
    val message: String? = null,
    val errors: List<GitHubErrorItemDto> = emptyList(),
)

@Serializable
private data class GitHubErrorItemDto(
    val resource: String? = null,
    val field: String? = null,
    val code: String? = null,
    val message: String? = null,
)
