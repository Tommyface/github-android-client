/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain

import com.gy.githubclient.core.mutation.MutationError
import com.gy.githubclient.feature.repo.domain.usecase.RepoWriteValidator
import com.gy.githubclient.feature.repo.domain.usecase.WriteErrorKind
import com.gy.githubclient.feature.repo.domain.usecase.toWriteError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P-205：写操作的错误归一化与仓库名/简介校验（纯逻辑）。 */
class RepoWriteValidationTest {
    @Test
    fun errorKindsAreClassifiedFromMutationError() {
        assertEquals(WriteErrorKind.UNAUTHORIZED, MutationError.Unauthorized.toWriteError().kind)
        assertEquals(WriteErrorKind.NOT_FOUND, MutationError.NotFound.toWriteError().kind)
        assertEquals(WriteErrorKind.NETWORK, MutationError.Network(cause = "timeout").toWriteError().kind)
        assertEquals(
            WriteErrorKind.SERVER,
            MutationError.Server(status = 502, message = "bad gateway").toWriteError().kind,
        )
        assertEquals(
            WriteErrorKind.RATE_LIMITED,
            MutationError.RateLimited(retryAfterSeconds = 42).toWriteError().kind,
        )
        assertEquals(
            WriteErrorKind.CONFLICT,
            MutationError.Conflict(message = "already exists").toWriteError().kind,
        )
    }

    @Test
    fun forbiddenKeepsMissingScopesAsDetail() {
        val error =
            MutationError
                .Forbidden(
                    reason = com.gy.githubclient.core.mutation.ForbiddenReason.MISSING_SCOPE,
                    missingScopes = listOf("repo", "workflow"),
                ).toWriteError()

        assertEquals(WriteErrorKind.FORBIDDEN, error.kind)
        assertEquals("repo, workflow", error.detail)
    }

    @Test
    fun validationFallsBackToFieldErrors() {
        val error =
            MutationError
                .Validation(
                    fieldErrors =
                        listOf(
                            com.gy.githubclient.core.mutation.FieldError(
                                field = "name",
                                code = "invalid",
                                message = "name already exists",
                            ),
                        ),
                ).toWriteError()

        assertEquals(WriteErrorKind.VALIDATION, error.kind)
        assertTrue(error.detail?.contains("name") == true)
    }

    @Test
    fun repositoryNameRulesAreEnforced() {
        assertTrue(RepoWriteValidator.validateName("") is RepoWriteValidator.Result.Invalid)
        assertTrue(
            RepoWriteValidator.validateName("a".repeat(101)) is RepoWriteValidator.Result.Invalid,
        )
        assertTrue(
            RepoWriteValidator.validateName("bad name!") is RepoWriteValidator.Result.Invalid,
        )
        assertTrue(RepoWriteValidator.validateName("ok-name_1.0") is RepoWriteValidator.Result.Valid)
    }

    @Test
    fun descriptionLengthIsCapped() {
        assertTrue(RepoWriteValidator.validateDescription(null) is RepoWriteValidator.Result.Valid)
        assertTrue(
            RepoWriteValidator.validateDescription("x".repeat(351)) is RepoWriteValidator.Result.Invalid,
        )
        assertTrue(
            RepoWriteValidator.validateDescription("x".repeat(350)) is RepoWriteValidator.Result.Valid,
        )
    }
}
