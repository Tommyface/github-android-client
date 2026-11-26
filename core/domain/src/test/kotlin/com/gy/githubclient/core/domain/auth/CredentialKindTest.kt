/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** M3-01：凭据类型判定与能力矩阵。 */
class CredentialKindTest {
    @Test
    fun detectsFineGrainedPat() {
        assertEquals(
            CredentialKind.FINE_GRAINED,
            CredentialKind.detect("github_pat_11ABCDEFG0aBcDeFgHiJkLmNoPqRsTuVwXyZ0123"),
        )
    }

    @Test
    fun detectsClassicPat() {
        assertEquals(CredentialKind.CLASSIC, CredentialKind.detect("ghp_16C7e42F292c6912E7710c838347Ae178B4a"))
    }

    @Test
    fun detectsOauthToken() {
        assertEquals(CredentialKind.OAUTH, CredentialKind.detect("a".repeat(40)))
    }

    @Test
    fun unknownForBlankOrUnrecognised() {
        assertEquals(CredentialKind.UNKNOWN, CredentialKind.detect(null))
        assertEquals(CredentialKind.UNKNOWN, CredentialKind.detect(""))
        assertEquals(CredentialKind.UNKNOWN, CredentialKind.detect("not-a-token"))
    }

    @Test
    fun fineGrainedCannotUseGraphQl() {
        assertFalse(CredentialKind.FINE_GRAINED.supportsGraphQl)
        assertTrue(CredentialKind.CLASSIC.supportsGraphQl)
        assertTrue(CredentialKind.OAUTH.supportsGraphQl)
    }

    @Test
    fun notificationsRequireClassicPat() {
        assertTrue(CredentialKind.CLASSIC.supportsNotifications)
        assertFalse(CredentialKind.FINE_GRAINED.supportsNotifications)
        assertFalse(CredentialKind.OAUTH.supportsNotifications)
    }

    @Test
    fun capabilityEvaluatorExplainsWhy() {
        assertTrue(
            CredentialCapabilityEvaluator.supports(
                CredentialKind.CLASSIC,
                CredentialCapability.CONTRIBUTION_HEATMAP,
            ),
        )
        val reason =
            CredentialCapabilityEvaluator.unavailableReason(
                CredentialKind.FINE_GRAINED,
                CredentialCapability.CONTRIBUTION_HEATMAP,
            )
        assertTrue("应说明原因", reason?.contains("GraphQL") == true)
        assertNull(
            CredentialCapabilityEvaluator.unavailableReason(
                CredentialKind.CLASSIC,
                CredentialCapability.NOTIFICATIONS,
            ),
        )
    }

    @Test
    fun writeCapabilityRequiresKnownKind() {
        assertFalse(
            CredentialCapabilityEvaluator.supports(
                CredentialKind.UNKNOWN,
                CredentialCapability.REPOSITORY_WRITE,
            ),
        )
        assertTrue(
            CredentialCapabilityEvaluator.supports(
                CredentialKind.FINE_GRAINED,
                CredentialCapability.REPOSITORY_WRITE,
            ),
        )
    }
}
