/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.permission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CapabilityEvaluatorTest {
    @Test
    fun publicReadNeedsNoScope() {
        val decision =
            CapabilityEvaluator.evaluate(
                RepoAction.READ_CODE,
                PermissionContext(
                    role = RepoRole.READ,
                    scopes = emptySet(),
                    scopesKnown = true,
                    isPrivate = false,
                ),
            )

        assertTrue(decision.allowed)
    }

    @Test
    fun privateReadRequiresRepoScope() {
        val denied =
            CapabilityEvaluator.evaluate(
                RepoAction.READ_CODE,
                PermissionContext(role = RepoRole.READ, scopesKnown = true, isPrivate = true),
            )
        assertEquals(DenyReason.MISSING_SCOPE, denied.reason)

        val allowed =
            CapabilityEvaluator.evaluate(
                RepoAction.READ_CODE,
                PermissionContext(
                    role = RepoRole.READ,
                    scopes = setOf("repo"),
                    scopesKnown = true,
                    isPrivate = true,
                ),
            )
        assertTrue(allowed.allowed)
    }

    @Test
    fun triageCannotWriteCodeButCanWriteIssues() {
        val context = PermissionContext(role = RepoRole.TRIAGE, scopes = setOf("repo"), scopesKnown = true)

        val writeCode = CapabilityEvaluator.evaluate(RepoAction.WRITE_CODE, context)
        assertFalse(writeCode.allowed)
        assertEquals(DenyReason.INSUFFICIENT_ROLE, writeCode.reason)

        val writeIssue = CapabilityEvaluator.evaluate(RepoAction.WRITE_ISSUES, context)
        assertTrue(writeIssue.allowed)
    }

    @Test
    fun triageCannotCreateRelease() {
        val decision =
            CapabilityEvaluator.evaluate(
                RepoAction.MANAGE_RELEASES,
                PermissionContext(role = RepoRole.TRIAGE, scopes = setOf("repo"), scopesKnown = true),
            )

        assertFalse(decision.allowed)
        assertEquals(RepoRole.PUSH, decision.requiredRole)
    }

    @Test
    fun adminCanManageSecrets() {
        val decision =
            CapabilityEvaluator.evaluate(
                RepoAction.MANAGE_ACTIONS_SECRETS,
                PermissionContext(role = RepoRole.ADMIN, scopes = setOf("repo"), scopesKnown = true),
            )

        assertTrue(decision.allowed)
    }

    @Test
    fun deleteRepoNeedsDeleteScopeEvenForAdmin() {
        val denied =
            CapabilityEvaluator.evaluate(
                RepoAction.DELETE_REPO,
                PermissionContext(role = RepoRole.ADMIN, scopes = setOf("repo"), scopesKnown = true),
            )
        assertEquals(DenyReason.MISSING_SCOPE, denied.reason)
        assertTrue(denied.requiredScopes.contains("delete_repo"))

        val allowed =
            CapabilityEvaluator.evaluate(
                RepoAction.DELETE_REPO,
                PermissionContext(role = RepoRole.ADMIN, scopes = setOf("delete_repo"), scopesKnown = true),
            )
        assertTrue(allowed.allowed)
    }

    @Test
    fun protectedBranchBlocksDirectWriteOnly() {
        val context =
            PermissionContext(
                role = RepoRole.PUSH,
                scopes = setOf("repo"),
                scopesKnown = true,
                defaultBranchProtected = true,
            )

        val direct = CapabilityEvaluator.evaluate(RepoAction.WRITE_PROTECTED_BRANCH, context)
        assertFalse(direct.allowed)
        assertEquals(DenyReason.PROTECTED_BRANCH, direct.reason)

        val branch = CapabilityEvaluator.evaluate(RepoAction.WRITE_CODE, context)
        assertTrue(branch.allowed)
    }

    @Test
    fun fineGrainedTokenSkipsScopeGate() {
        val decision =
            CapabilityEvaluator.evaluate(
                RepoAction.WRITE_CODE,
                PermissionContext(
                    role = RepoRole.PUSH,
                    scopes = emptySet(),
                    scopesKnown = false,
                    isPrivate = true,
                ),
            )

        assertTrue(decision.allowed)
    }

    @Test
    fun publicWriteNeedsPublicRepoOrRepo() {
        val denied =
            CapabilityEvaluator.evaluate(
                RepoAction.WRITE_CODE,
                PermissionContext(
                    role = RepoRole.PUSH,
                    scopes = emptySet(),
                    scopesKnown = true,
                    isPrivate = false,
                ),
            )
        assertEquals(DenyReason.MISSING_SCOPE, denied.reason)

        val allowed =
            CapabilityEvaluator.evaluate(
                RepoAction.WRITE_CODE,
                PermissionContext(
                    role = RepoRole.PUSH,
                    scopes = setOf("public_repo"),
                    scopesKnown = true,
                    isPrivate = false,
                ),
            )
        assertTrue(allowed.allowed)
    }

    @Test
    fun webhookAcceptsAdminHookScope() {
        val decision =
            CapabilityEvaluator.evaluate(
                RepoAction.MANAGE_WEBHOOKS,
                PermissionContext(
                    role = RepoRole.ADMIN,
                    scopes = setOf("admin:repo_hook"),
                    scopesKnown = true,
                ),
            )

        assertTrue(decision.allowed)
    }

    @Test
    fun noRoleDeniesEverything() {
        val decision =
            CapabilityEvaluator.evaluate(
                RepoAction.READ_ISSUES,
                PermissionContext(role = RepoRole.NONE, scopes = setOf("repo"), scopesKnown = true),
            )

        assertFalse(decision.allowed)
        assertEquals(DenyReason.INSUFFICIENT_ROLE, decision.reason)
    }
}
