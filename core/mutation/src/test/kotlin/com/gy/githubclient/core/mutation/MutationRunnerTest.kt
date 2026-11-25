/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.mutation

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 写操作信封：重试、审计、错误文案。 */
class MutationRunnerTest {
    private class RecordingSink : AuditSink {
        val entries = mutableListOf<AuditEntry>()

        override suspend fun record(entry: AuditEntry) {
            entries += entry
        }
    }

    private val spec = MutationSpec(action = "star", target = "gy/demo", idempotent = true)

    @Test
    fun successIsAuditedOnce() =
        runTest {
            val sink = RecordingSink()
            val runner = MutationRunner(auditSink = sink, clock = { 42L }, sleeper = {})

            val result =
                runner.execute(spec) { attempt ->
                    MutationAttempt.Success("ok", statusCode = 204, requestId = "req-1")
                }

            assertEquals(MutationResult.Success("ok"), result)
            val entry = sink.entries.single()
            assertTrue(entry.success)
            assertEquals("star", entry.action)
            assertEquals("gy/demo", entry.target)
            assertEquals(204, entry.statusCode)
            assertEquals("req-1", entry.requestId)
            assertEquals(42L, entry.timestampMillis)
            assertEquals(1, entry.attempts)
        }

    @Test
    fun idempotentServerErrorRetriesThenSucceeds() =
        runTest {
            val sink = RecordingSink()
            val delays = mutableListOf<Long>()
            val runner =
                MutationRunner(
                    retryPolicy = MutationRetryPolicy(maxAttempts = 3, baseDelayMillis = 10, maxDelayMillis = 100),
                    auditSink = sink,
                    sleeper = { delays += it },
                )
            var attempts = 0

            val result =
                runner.execute(spec) { attempt ->
                    attempts = attempt
                    if (attempt < 3) {
                        MutationAttempt.Failure(MutationError.Server(502), statusCode = 502)
                    } else {
                        MutationAttempt.Success(Unit)
                    }
                }

            assertEquals(MutationResult.Success(Unit), result)
            assertEquals(3, attempts)
            assertEquals(listOf(10L, 20L), delays)
            assertEquals(3, sink.entries.single().attempts)
        }

    @Test
    fun nonIdempotentNetworkErrorIsAuditedWithoutRetry() =
        runTest {
            val sink = RecordingSink()
            var calls = 0
            val runner = MutationRunner(auditSink = sink, sleeper = {})
            val createSpec = spec.copy(action = "issue.create", idempotent = false)

            val result =
                runner.execute(createSpec) {
                    calls++
                    MutationAttempt.Failure(MutationError.Network("timeout"))
                }

            assertTrue(result is MutationResult.Failure)
            assertEquals(1, calls)
            val entry = sink.entries.single()
            assertFalse(entry.success)
            assertEquals(1, entry.attempts)
            assertEquals("网络错误：timeout", entry.message)
        }

    @Test
    fun rateLimitHonorsRetryAfterEvenWhenNonIdempotent() =
        runTest {
            val delays = mutableListOf<Long>()
            val runner =
                MutationRunner(
                    retryPolicy = MutationRetryPolicy(maxAttempts = 2, baseDelayMillis = 0, maxDelayMillis = 5_000),
                    sleeper = { delays += it },
                )
            val createSpec = spec.copy(idempotent = false)

            val result =
                runner.execute(createSpec) { attempt ->
                    if (attempt == 1) {
                        MutationAttempt.Failure(MutationError.RateLimited(retryAfterSeconds = 2))
                    } else {
                        MutationAttempt.Success(Unit)
                    }
                }

            assertEquals(MutationResult.Success(Unit), result)
            assertEquals(listOf(2_000L), delays)
        }

    @Test
    fun missingScopeMessageTellsUserHowToFix() {
        val error =
            MutationError.Forbidden(
                reason = ForbiddenReason.MISSING_SCOPE,
                missingScopes = listOf("user:follow"),
            )

        assertTrue(error.userMessage().contains("user:follow"))
        assertTrue(error.userMessage().contains("重新授权"))
    }

    @Test
    fun validationMessageListsFields() {
        val error =
            MutationError.Validation(
                fieldErrors = listOf(FieldError("title", "missing"), FieldError("title", "invalid")),
            )

        assertEquals("字段校验失败：title", error.userMessage())
    }
}
