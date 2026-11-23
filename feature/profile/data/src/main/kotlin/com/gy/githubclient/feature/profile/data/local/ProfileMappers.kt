/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.data.local

import com.gy.githubclient.feature.profile.domain.model.UserProfile

fun UserProfile.toEntity(): ProfileEntity =
    ProfileEntity(
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

fun ProfileEntity.toDomain(): UserProfile =
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
