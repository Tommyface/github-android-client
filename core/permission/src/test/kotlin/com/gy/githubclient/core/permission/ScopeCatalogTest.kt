/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.permission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Scope 目录与仓库角色解析的纯逻辑测试。 */
class ScopeCatalogTest {
    @Test
    fun allRecommendedScopesSatisfyEveryFeature() {
        val statuses = ScopeCatalog.assess(ScopeCatalog.recommendedScopes)

        assertEquals(ScopeFeature.entries.size, statuses.count { it.granted })
        assertTrue(ScopeCatalog.missingScopes(ScopeCatalog.recommendedScopes).isEmpty())
    }

    @Test
    fun repoScopeCoversReadAndWrite() {
        val statuses = ScopeCatalog.assess(listOf("repo", "read:user", "notifications", "workflow", "gist", "read:org", "user:follow"))

        assertTrue(statuses.first { it.feature == ScopeFeature.REPO_READ }.granted)
        assertTrue(statuses.first { it.feature == ScopeFeature.REPO_WRITE }.granted)
    }

    @Test
    fun publicRepoOnlyCoversPublicReadAndWrite() {
        val statuses = ScopeCatalog.assess(listOf("public_repo"))

        assertTrue(statuses.first { it.feature == ScopeFeature.REPO_READ }.granted)
        assertTrue(statuses.first { it.feature == ScopeFeature.REPO_WRITE }.granted)
    }

    @Test
    fun followRequiresUserFollowScope() {
        val statuses = ScopeCatalog.assess(listOf("repo", "read:user", "user"))

        assertFalse(statuses.first { it.feature == ScopeFeature.FOLLOW }.granted)
        assertTrue(ScopeCatalog.missingScopes(listOf("repo", "read:user", "user")).contains("user:follow"))
    }

    @Test
    fun missingScopesAreDeduplicatedAndPreferRepo() {
        val missing = ScopeCatalog.missingScopes(emptyList())

        assertTrue(missing.contains("repo"))
        assertEquals(missing.distinct(), missing)
        assertFalse(missing.contains("public_repo"))
    }

    @Test
    fun whitespaceAndDuplicatesAreNormalized() {
        val statuses = ScopeCatalog.assess(listOf(" repo ", "repo", "read:user"))

        assertTrue(statuses.first { it.feature == ScopeFeature.REPO_WRITE }.granted)
        assertEquals("repo · read:user", ScopeCatalog.summary(listOf(" repo ", "repo", "read:user")))
    }

    @Test
    fun emptyScopesShowNoSummary() {
        assertEquals("无", ScopeCatalog.summary(emptyList()))
    }

    @Test
    fun roleResolverPrefersHighestRole() {
        assertEquals(RepoRole.ADMIN, RepoRoleResolver.resolve(RepoPermissionFlags(admin = true, push = true, pull = true)))
        assertEquals(RepoRole.MAINTAIN, RepoRoleResolver.resolve(RepoPermissionFlags(maintain = true, push = true)))
        assertEquals(RepoRole.PUSH, RepoRoleResolver.resolve(RepoPermissionFlags(push = true, triage = true)))
        assertEquals(RepoRole.TRIAGE, RepoRoleResolver.resolve(RepoPermissionFlags(triage = true, pull = true)))
        assertEquals(RepoRole.READ, RepoRoleResolver.resolve(RepoPermissionFlags(pull = true)))
        assertEquals(RepoRole.NONE, RepoRoleResolver.resolve(RepoPermissionFlags()))
        assertEquals(RepoRole.NONE, RepoRoleResolver.resolve(null))
    }

    @Test
    fun roleLabelsAreReadable() {
        assertEquals("管理员", RepoRole.ADMIN.displayLabel)
        assertEquals("只读", RepoRole.READ.displayLabel)
        assertEquals("无权限", RepoRole.NONE.displayLabel)
    }
}
