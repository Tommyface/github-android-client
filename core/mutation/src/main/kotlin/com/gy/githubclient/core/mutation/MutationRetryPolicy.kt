/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.mutation

data class RetryDecision(
    val shouldRetry: Boolean,
    val delayMillis: Long = 0L,
)

/**
 * 写操作重试策略。
 *
 * 核心约束：非幂等写操作（创建 Issue、合并 PR 等）只在限流时重试，
 * 网络错误/5xx 交给上层做「刷新对账」，避免重复提交。
 */
class MutationRetryPolicy(
    private val maxAttempts: Int = DEFAULT_MAX_ATTEMPTS,
    private val baseDelayMillis: Long = DEFAULT_BASE_DELAY_MILLIS,
    private val maxDelayMillis: Long = DEFAULT_MAX_DELAY_MILLIS,
) {
    init {
        require(maxAttempts >= 1) { "maxAttempts must be >= 1" }
        require(baseDelayMillis >= 0) { "baseDelayMillis must be >= 0" }
        require(maxDelayMillis >= baseDelayMillis) { "maxDelayMillis must be >= baseDelayMillis" }
    }

    fun decide(
        attempt: Int,
        error: MutationError,
        idempotent: Boolean,
    ): RetryDecision {
        if (attempt >= maxAttempts) return RetryDecision(shouldRetry = false)
        return when (error) {
            is MutationError.RateLimited ->
                RetryDecision(
                    shouldRetry = true,
                    delayMillis = rateLimitDelay(error.retryAfterSeconds, attempt),
                )
            is MutationError.Server ->
                retryIfIdempotent(idempotent, attempt)
            is MutationError.Network ->
                retryIfIdempotent(idempotent, attempt)
            else -> RetryDecision(shouldRetry = false)
        }
    }

    private fun retryIfIdempotent(
        idempotent: Boolean,
        attempt: Int,
    ): RetryDecision =
        if (idempotent) {
            RetryDecision(shouldRetry = true, delayMillis = backoff(attempt))
        } else {
            RetryDecision(shouldRetry = false)
        }

    private fun rateLimitDelay(
        retryAfterSeconds: Long?,
        attempt: Int,
    ): Long =
        retryAfterSeconds
            ?.times(MILLIS_PER_SECOND)
            ?.coerceAtMost(maxDelayMillis)
            ?: backoff(attempt)

    private fun backoff(attempt: Int): Long {
        var delay = baseDelayMillis
        repeat((attempt - 1).coerceAtLeast(0)) {
            delay = (delay * 2).coerceAtMost(maxDelayMillis)
        }
        return delay.coerceAtMost(maxDelayMillis)
    }

    private companion object {
        const val DEFAULT_MAX_ATTEMPTS = 3
        const val DEFAULT_BASE_DELAY_MILLIS = 500L
        const val DEFAULT_MAX_DELAY_MILLIS = 8_000L
        const val MILLIS_PER_SECOND = 1_000L
    }
}
