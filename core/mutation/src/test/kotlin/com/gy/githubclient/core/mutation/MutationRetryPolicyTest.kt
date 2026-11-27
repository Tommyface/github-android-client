/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.mutation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MutationRetryPolicyTest {
    private val policy =
        MutationRetryPolicy(
            maxAttempts = 3,
            baseDelayMillis = 500,
            maxDelayMillis = 4_000,
        )

    @Test
    fun nonIdempotentServerErrorIsNotRetried() {
        val decision = policy.decide(attempt = 1, error = MutationError.Server(502), idempotent = false)

        assertFalse(decision.shouldRetry)
    }

    @Test
    fun idempotentServerErrorUsesExponentialBackoff() {
        val first = policy.decide(attempt = 1, error = MutationError.Server(502), idempotent = true)
        val second = policy.decide(attempt = 2, error = MutationError.Server(502), idempotent = true)

        assertTrue(first.shouldRetry)
        assertEquals(500L, first.delayMillis)
        assertEquals(1_000L, second.delayMillis)
    }

    @Test
    fun networkErrorRetriesOnlyWhenIdempotent() {
        val idempotent = policy.decide(attempt = 1, error = MutationError.Network("timeout"), idempotent = true)
        val unsafe = policy.decide(attempt = 1, error = MutationError.Network("timeout"), idempotent = false)

        assertTrue(idempotent.shouldRetry)
        assertFalse(unsafe.shouldRetry)
    }

    @Test
    fun validationIsNeverRetried() {
        val decision = policy.decide(attempt = 1, error = MutationError.Validation("bad"), idempotent = true)

        assertFalse(decision.shouldRetry)
    }

    @Test
    fun rateLimitUsesRetryAfter() {
        val decision =
            policy.decide(
                attempt = 1,
                error = MutationError.RateLimited(retryAfterSeconds = 2),
                idempotent = false,
            )

        assertTrue(decision.shouldRetry)
        assertEquals(2_000L, decision.delayMillis)
    }

    @Test
    fun rateLimitDelayIsCapped() {
        val decision =
            policy.decide(
                attempt = 1,
                error = MutationError.RateLimited(retryAfterSeconds = 60),
                idempotent = false,
            )

        assertEquals(4_000L, decision.delayMillis)
    }

    @Test
    fun noRetryAfterMaxAttempts() {
        val decision = policy.decide(attempt = 3, error = MutationError.RateLimited(1), idempotent = true)

        assertFalse(decision.shouldRetry)
    }

    @Test
    fun forbiddenAndConflictAreNotRetried() {
        assertFalse(policy.decide(1, MutationError.Forbidden(ForbiddenReason.OTHER), true).shouldRetry)
        assertFalse(policy.decide(1, MutationError.Conflict("sha"), true).shouldRetry)
        assertFalse(policy.decide(1, MutationError.Unauthorized, true).shouldRetry)
    }
}
