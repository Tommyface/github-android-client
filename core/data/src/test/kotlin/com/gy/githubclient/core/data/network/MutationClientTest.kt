/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.data.network

import com.gy.githubclient.core.domain.network.ApiChannel
import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.core.mutation.AuditEntry
import com.gy.githubclient.core.mutation.AuditSink
import com.gy.githubclient.core.mutation.ForbiddenReason
import com.gy.githubclient.core.mutation.MutationError
import com.gy.githubclient.core.mutation.MutationResult
import com.gy.githubclient.core.mutation.MutationRetryPolicy
import com.gy.githubclient.core.mutation.MutationSpec
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 写通道契约测试：401/403/409/422/429/5xx 全链路与审计。 */
class MutationClientTest {
    private val spec = MutationSpec(action = "watch", target = "gy/demo")

    @Test
    fun successAuditsWithStatusAndRequestId() =
        runTest {
            val sink = RecordingSink()
            val engine =
                MockEngine {
                    respond(
                        "",
                        HttpStatusCode.NoContent,
                        headersOf("X-GitHub-Request-Id", "req-9"),
                    )
                }
            val client = buildClient(engine, sink)

            val result =
                client.mutate(spec, HttpMethod.Put, "/repos/gy/demo/subscription", body = "{}") { Unit }

            assertTrue(result is MutationResult.Success)
            val entry = sink.entries.single()
            assertTrue(entry.success)
            assertEquals(204, entry.statusCode)
            assertEquals("req-9", entry.requestId)
            assertEquals("watch", entry.action)
        }

    @Test
    fun unauthorizedTriggersSessionCallback() =
        runTest {
            val provider = FakeTokenProvider("expired")
            val sink = RecordingSink()
            val engine = MockEngine { respond("{}", HttpStatusCode.Unauthorized) }
            val client = buildClient(engine, sink, provider)

            val result = client.mutate(spec, HttpMethod.Put, "/x") { Unit }

            assertTrue((result as MutationResult.Failure).error is MutationError.Unauthorized)
            assertEquals(ApiChannel.PRIMARY, provider.unauthorizedChannel)
            assertTrue(!sink.entries.single().success)
        }

    @Test
    fun forbiddenCarriesMissingScopes() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        """{"message":"Resource not accessible by integration"}""",
                        HttpStatusCode.Forbidden,
                        headersOf(
                            "X-Accepted-OAuth-Scopes" to listOf("user:follow"),
                            "X-OAuth-Scopes" to listOf("repo, read:user"),
                        ),
                    )
                }
            val client = buildClient(engine, RecordingSink())

            val result = client.mutate(spec, HttpMethod.Put, "/user/following/octocat") { Unit }

            val error = (result as MutationResult.Failure).error as MutationError.Forbidden
            assertEquals(ForbiddenReason.MISSING_SCOPE, error.reason)
            assertEquals(listOf("user:follow"), error.missingScopes)
        }

    @Test
    fun conflictAndValidationAreDistinct() =
        runTest {
            val conflictEngine =
                MockEngine { respond("""{"message":"sha does not match"}""", HttpStatusCode.Conflict) }
            val conflict =
                buildClient(conflictEngine, RecordingSink())
                    .mutate(spec, HttpMethod.Put, "/contents/a.md") { Unit }
            assertEquals("sha does not match", ((conflict as MutationResult.Failure).error as MutationError.Conflict).message)

            val validationEngine =
                MockEngine {
                    respond(
                        """{"message":"Validation Failed","errors":[{"field":"title","code":"missing"}]}""",
                        HttpStatusCode.UnprocessableEntity,
                    )
                }
            val validation =
                buildClient(validationEngine, RecordingSink())
                    .mutate(spec, HttpMethod.Post, "/issues") { Unit }
            val error = (validation as MutationResult.Failure).error as MutationError.Validation
            assertEquals("title", error.fieldErrors.single().field)
            assertEquals("missing", error.fieldErrors.single().code)
        }

    @Test
    fun rateLimitRetriesThenSucceeds() =
        runTest {
            var calls = 0
            val engine =
                MockEngine {
                    calls++
                    if (calls == 1) {
                        respond(
                            """{"message":"slow down"}""",
                            HttpStatusCode.TooManyRequests,
                            headersOf("Retry-After", "0"),
                        )
                    } else {
                        respond("", HttpStatusCode.NoContent)
                    }
                }
            val client =
                buildClient(
                    engine = engine,
                    sink = RecordingSink(),
                    policy = MutationRetryPolicy(maxAttempts = 3, baseDelayMillis = 0, maxDelayMillis = 0),
                )

            val result = client.mutate(spec.copy(idempotent = false), HttpMethod.Put, "/x") { Unit }

            assertTrue(result is MutationResult.Success)
            assertEquals(2, calls)
        }

    @Test
    fun serverErrorRetriesOnlyWhenIdempotent() =
        runTest {
            var safeCalls = 0
            val safeEngine =
                MockEngine {
                    safeCalls++
                    if (safeCalls == 1) {
                        respond("{}", HttpStatusCode.BadGateway)
                    } else {
                        respond("", HttpStatusCode.NoContent)
                    }
                }
            val safe =
                buildClient(
                    safeEngine,
                    RecordingSink(),
                    policy = MutationRetryPolicy(maxAttempts = 3, baseDelayMillis = 0, maxDelayMillis = 0),
                ).mutate(spec, HttpMethod.Put, "/x") { Unit }
            assertTrue(safe is MutationResult.Success)
            assertEquals(2, safeCalls)

            var unsafeCalls = 0
            val unsafeEngine =
                MockEngine {
                    unsafeCalls++
                    respond("{}", HttpStatusCode.BadGateway)
                }
            val unsafe =
                buildClient(unsafeEngine, RecordingSink())
                    .mutate(spec.copy(idempotent = false), HttpMethod.Post, "/issues") { Unit }
            assertTrue(unsafe is MutationResult.Failure)
            assertEquals(1, unsafeCalls)
        }

    @Test
    fun missingTokenIsAuditedWithoutRequest() =
        runTest {
            var requested = false
            val sink = RecordingSink()
            val engine =
                MockEngine {
                    requested = true
                    respond("", HttpStatusCode.OK)
                }
            val client = buildClient(engine, sink, FakeTokenProvider(null))

            val result = client.mutate(spec, HttpMethod.Put, "/x") { Unit }

            assertTrue((result as MutationResult.Failure).error is MutationError.Unauthorized)
            assertTrue(!requested)
            assertEquals("登录已失效，请重新登录", sink.entries.single().message)
        }

    private class RecordingSink : AuditSink {
        val entries = mutableListOf<AuditEntry>()

        override suspend fun record(entry: AuditEntry) {
            entries += entry
        }
    }

    private fun buildClient(
        engine: MockEngine,
        sink: AuditSink,
        provider: AuthTokenProvider = FakeTokenProvider("ghp_test"),
        policy: MutationRetryPolicy = MutationRetryPolicy(maxAttempts = 3, baseDelayMillis = 0, maxDelayMillis = 0),
    ): GitHubApiClient =
        GitHubApiClient(
            httpClient = HttpClient(engine) { expectSuccess = false },
            tokenProvider = provider,
            maxRetries = 0,
            auditSink = sink,
            mutationRetryPolicy = policy,
        )

    private class FakeTokenProvider(
        private val token: String?,
    ) : AuthTokenProvider {
        var unauthorizedChannel: ApiChannel? = null
            private set

        override suspend fun tokenFor(channel: ApiChannel): String? = token

        override suspend fun onUnauthorized(channel: ApiChannel) {
            unauthorizedChannel = channel
        }
    }
}
