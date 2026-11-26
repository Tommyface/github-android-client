/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.mutation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MutationErrorMapperTest {
    @Test
    fun unauthorizedMapsToUnauthorized() {
        assertEquals(MutationError.Unauthorized, MutationErrorMapper.map(401))
    }

    @Test
    fun rateLimitByRemainingMapsToRateLimited() {
        val error =
            MutationErrorMapper.map(
                status = 403,
                headers = MutationHeaders(rateLimitRemaining = 0, rateLimitResetEpochSeconds = 1_700_000_000),
            )

        assertEquals(MutationError.RateLimited(remaining = 0, resetEpochSeconds = 1_700_000_000), error)
    }

    @Test
    fun rateLimitByRetryAfterMapsToRateLimited() {
        val error =
            MutationErrorMapper.map(
                status = 429,
                headers = MutationHeaders(retryAfterSeconds = 30),
            )

        assertEquals(MutationError.RateLimited(retryAfterSeconds = 30), error)
    }

    @Test
    fun forbiddenWithAcceptedScopesMeansMissingScope() {
        val error =
            MutationErrorMapper.map(
                status = 403,
                payload = GitHubErrorPayload(message = "Resource not accessible"),
                headers = MutationHeaders(acceptedOAuthScopes = setOf("issues:write")),
            )

        assertTrue(error is MutationError.Forbidden)
        assertEquals(ForbiddenReason.MISSING_SCOPE, (error as MutationError.Forbidden).reason)
    }

    @Test
    fun protectedBranchForbiddenIsDetectedFromMessage() {
        val error =
            MutationErrorMapper.map(
                status = 403,
                payload = GitHubErrorPayload(message = "Cannot force-push to a protected branch"),
            )

        assertEquals(
            MutationError.Forbidden(ForbiddenReason.PROTECTED_BRANCH, "Cannot force-push to a protected branch"),
            error,
        )
    }

    @Test
    fun permissionFieldMeansInsufficientRole() {
        val error =
            MutationErrorMapper.map(
                status = 403,
                payload =
                    GitHubErrorPayload(
                        message = "Forbidden",
                        errors = listOf(FieldError(field = "permissions", code = "missing")),
                    ),
            )

        assertTrue(error is MutationError.Forbidden)
        assertEquals(ForbiddenReason.INSUFFICIENT_ROLE, (error as MutationError.Forbidden).reason)
    }

    @Test
    fun notFoundAndConflictAreMapped() {
        assertEquals(MutationError.NotFound, MutationErrorMapper.map(404))
        assertEquals(MutationError.Conflict("sha does not match"), MutationErrorMapper.map(409, GitHubErrorPayload("sha does not match")))
    }

    @Test
    fun validationKeepsFieldErrors() {
        val fieldError = FieldError(field = "title", code = "missing", message = "title is required")
        val error =
            MutationErrorMapper.map(
                status = 422,
                payload = GitHubErrorPayload(message = "Validation Failed", errors = listOf(fieldError)),
            )

        assertEquals(MutationError.Validation("Validation Failed", listOf(fieldError)), error)
    }

    @Test
    fun serverAndUnknownAreMapped() {
        assertEquals(MutationError.Server(503, "unavailable"), MutationErrorMapper.map(503, GitHubErrorPayload("unavailable")))
        assertEquals(MutationError.Unknown(418, "teapot"), MutationErrorMapper.map(418, GitHubErrorPayload("teapot")))
    }

    @Test
    fun headersAreParsedCaseInsensitively() {
        val headers =
            MutationHeaders.fromRaw(
                mapOf(
                    "Retry-After" to "7",
                    "X-RateLimit-Remaining" to "0",
                    "X-RateLimit-Reset" to "1700000000",
                    "X-Accepted-OAuth-Scopes" to "repo, workflow",
                    "X-OAuth-Scopes" to "repo workflow notifications",
                ),
            )

        assertEquals(7L, headers.retryAfterSeconds)
        assertEquals(0, headers.rateLimitRemaining)
        assertEquals(1_700_000_000L, headers.rateLimitResetEpochSeconds)
        assertEquals(setOf("repo", "workflow"), headers.acceptedOAuthScopes)
        assertEquals(setOf("repo", "workflow", "notifications"), headers.oauthScopes)
    }
}
