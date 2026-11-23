/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.data.remote

import com.gy.githubclient.feature.profile.domain.model.UserProfile
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /user 响应 DTO。默认值保证缺失字段可安全反序列化。 */
@Suppress("LongParameterList")
@Serializable
data class UserDto(
    val id: Long = 0L,
    val login: String = "",
    val name: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val bio: String? = null,
    val company: String? = null,
    val location: String? = null,
    val blog: String? = null,
    val email: String? = null,
    @SerialName("public_repos") val publicRepos: Int = 0,
    @SerialName("public_gists") val publicGists: Int = 0,
    val followers: Int = 0,
    val following: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

fun UserDto.toDomain(): UserProfile =
    UserProfile(
        id = id,
        login = login,
        name = name,
        avatarUrl = avatarUrl,
        htmlUrl = htmlUrl,
        bio = bio,
        company = company,
        location = location,
        blog = blog,
        email = email,
        publicRepos = publicRepos,
        publicGists = publicGists,
        followers = followers,
        following = following,
        createdAt = createdAt,
    )
