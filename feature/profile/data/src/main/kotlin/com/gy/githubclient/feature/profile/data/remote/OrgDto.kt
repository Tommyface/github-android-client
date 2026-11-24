/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.data.remote

import com.gy.githubclient.feature.profile.domain.model.OrgMember
import com.gy.githubclient.feature.profile.domain.model.OrgSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /orgs/{org} 响应 DTO（v0.15.0 A5）。 */
@Suppress("LongParameterList")
@Serializable
data class OrgDto(
    val login: String = "",
    val name: String? = null,
    val description: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("public_repos") val publicRepos: Int = 0,
    val followers: Int = 0,
    val location: String? = null,
    val blog: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

fun OrgDto.toDomain(): OrgSummary =
    OrgSummary(
        login = login,
        name = name,
        description = description,
        avatarUrl = avatarUrl,
        htmlUrl = htmlUrl,
        publicRepos = publicRepos,
        followers = followers,
        location = location,
        blog = blog,
        createdAt = createdAt,
    )

/** GET /orgs/{org}/members 单条 DTO（v0.15.0 A5）。 */
@Serializable
data class OrgMemberDto(
    val login: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
)

fun OrgMemberDto.toDomain(): OrgMember =
    OrgMember(
        login = login,
        avatarUrl = avatarUrl,
        htmlUrl = htmlUrl,
    )
