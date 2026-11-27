/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.mutation

/** 与写操作相关的响应头，由数据层从 HTTP 响应中提取。 */
data class MutationHeaders(
    val retryAfterSeconds: Long? = null,
    val rateLimitRemaining: Int? = null,
    val rateLimitResetEpochSeconds: Long? = null,
    val acceptedOAuthScopes: Set<String> = emptySet(),
    val oauthScopes: Set<String> = emptySet(),
) {
    companion object {
        fun fromRaw(headers: Map<String, String>): MutationHeaders {
            val normalized = headers.entries.associate { it.key.lowercase() to it.value }
            return MutationHeaders(
                retryAfterSeconds = normalized["retry-after"]?.trim()?.toLongOrNull(),
                rateLimitRemaining = normalized["x-ratelimit-remaining"]?.trim()?.toIntOrNull(),
                rateLimitResetEpochSeconds = normalized["x-ratelimit-reset"]?.trim()?.toLongOrNull(),
                acceptedOAuthScopes = parseScopes(normalized["x-accepted-oauth-scopes"]),
                oauthScopes = parseScopes(normalized["x-oauth-scopes"]),
            )
        }

        private fun parseScopes(raw: String?): Set<String> =
            raw
                ?.split(',', ' ')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                ?.toSet()
                .orEmpty()
    }
}

/** HTTP 状态码 + 错误体 + 响应头 → 统一错误模型。 */
object MutationErrorMapper {
    fun map(
        status: Int,
        payload: GitHubErrorPayload? = null,
        headers: MutationHeaders = MutationHeaders(),
    ): MutationError {
        val message = payload?.message
        return when {
            status == STATUS_UNAUTHORIZED -> MutationError.Unauthorized
            status == STATUS_FORBIDDEN -> forbidden(payload, headers, message)
            status == STATUS_NOT_FOUND -> MutationError.NotFound
            status == STATUS_CONFLICT -> MutationError.Conflict(message)
            status == STATUS_UNPROCESSABLE -> MutationError.Validation(message, payload?.errors.orEmpty())
            status == STATUS_TOO_MANY_REQUESTS -> rateLimited(headers)
            status in SERVER_ERROR_RANGE -> MutationError.Server(status, message)
            else -> MutationError.Unknown(status, message)
        }
    }

    private fun forbidden(
        payload: GitHubErrorPayload?,
        headers: MutationHeaders,
        message: String?,
    ): MutationError {
        if (isRateLimited(headers)) return rateLimited(headers)
        val reason =
            when {
                headers.acceptedOAuthScopes.isNotEmpty() -> ForbiddenReason.MISSING_SCOPE
                message.orEmpty().contains("protected branch", ignoreCase = true) ->
                    ForbiddenReason.PROTECTED_BRANCH
                payload?.errors.orEmpty().any { it.field.contains("permission", ignoreCase = true) } ->
                    ForbiddenReason.INSUFFICIENT_ROLE
                else -> ForbiddenReason.OTHER
            }
        return MutationError.Forbidden(
            reason = reason,
            message = message,
            missingScopes = missingScopes(headers),
        )
    }

    /** 服务端要求的 scope 减去当前凭证已授予的 scope。 */
    private fun missingScopes(headers: MutationHeaders): List<String> {
        if (headers.acceptedOAuthScopes.isEmpty()) return emptyList()
        val missing =
            if (headers.oauthScopes.isEmpty()) {
                headers.acceptedOAuthScopes
            } else {
                headers.acceptedOAuthScopes - headers.oauthScopes
            }
        return missing.sorted()
    }

    private fun rateLimited(headers: MutationHeaders): MutationError.RateLimited =
        MutationError.RateLimited(
            retryAfterSeconds = headers.retryAfterSeconds,
            remaining = headers.rateLimitRemaining,
            resetEpochSeconds = headers.rateLimitResetEpochSeconds,
        )

    private fun isRateLimited(headers: MutationHeaders): Boolean =
        headers.rateLimitRemaining == 0 || headers.retryAfterSeconds != null

    private const val STATUS_UNAUTHORIZED = 401
    private const val STATUS_FORBIDDEN = 403
    private const val STATUS_NOT_FOUND = 404
    private const val STATUS_CONFLICT = 409
    private const val STATUS_UNPROCESSABLE = 422
    private const val STATUS_TOO_MANY_REQUESTS = 429
    private val SERVER_ERROR_RANGE = 500..599
}
