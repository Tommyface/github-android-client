/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.data.network

import com.gy.githubclient.core.domain.network.ApiError
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlin.math.max

/** 把 HTTP 响应 / 异常映射为统一 [ApiError] 的纯函数集合。 */
object ApiErrorMapper {
    private const val MILLIS_PER_SECOND = 1000L

    /**
     * 根据状态码与限流头映射错误。非 2xx 时才调用。
     * 403 且 X-RateLimit-Remaining=0 → RateLimited；否则 403 视为 Http。
     */
    fun fromResponse(response: HttpResponse): ApiError {
        val status = response.status
        return when {
            status == HttpStatusCode.Unauthorized -> ApiError.Unauthorized
            status == HttpStatusCode.NotFound -> ApiError.NotFound
            status == HttpStatusCode.TooManyRequests -> ApiError.RateLimited(retryAfterOf(response))
            status == HttpStatusCode.Forbidden && isRateLimited(response) ->
                ApiError.RateLimited(retryAfterOf(response))

            else -> ApiError.Http(status.value, status.description)
        }
    }

    /** 从异常映射：网络类异常 vs 未知异常。 */
    fun fromException(throwable: Throwable): ApiError =
        ApiError.Network(throwable.message ?: throwable::class.simpleName ?: "network error")

    /** 是否因额度耗尽被限流。 */
    fun isRateLimited(response: HttpResponse): Boolean =
        response.headers["X-RateLimit-Remaining"] == "0"

    /**
     * 计算距离可重试的秒数。
     *
     * 优先级：`Retry-After`（秒数或 HTTP 日期，服务端明确指示）> `X-RateLimit-Reset`（额度重置时刻）。
     * 两者都没有 → null，调用方回退到指数退避。
     */
    fun retryAfterOf(
        response: HttpResponse,
        nowMillis: Long = System.currentTimeMillis(),
    ): Long? {
        response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()?.let { return it.coerceAtLeast(0L) }
        return response.headers["X-RateLimit-Reset"]?.toLongOrNull()?.let { resetEpoch ->
            max(0L, resetEpoch - nowMillis / MILLIS_PER_SECOND)
        }
    }
}
