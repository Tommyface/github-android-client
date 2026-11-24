/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.domain.model

/** 组织概要（v0.15.0 A5）：对应 GET /orgs/{org} 的最小只读视图。 */
@Suppress("LongParameterList")
data class OrgSummary(
    val login: String,
    val name: String?,
    val description: String?,
    val avatarUrl: String?,
    val htmlUrl: String,
    val publicRepos: Int,
    val followers: Int,
    val location: String?,
    val blog: String?,
    val createdAt: String?,
)

/** 组织成员（v0.15.0 A5）：对应 GET /orgs/{org}/members 的单条。 */
data class OrgMember(
    val login: String,
    val avatarUrl: String?,
    val htmlUrl: String,
)
