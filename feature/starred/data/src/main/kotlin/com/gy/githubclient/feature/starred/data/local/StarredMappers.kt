/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.starred.data.local

import com.gy.githubclient.feature.repo.domain.model.RepoOwner
import com.gy.githubclient.feature.repo.domain.model.RepoVisibility
import com.gy.githubclient.feature.repo.domain.model.Repository

fun Repository.toEntity(
    bucket: String,
    page: Int,
): StarredEntity =
    StarredEntity(
        id = id,
        name = name,
        fullName = fullName,
        ownerLogin = owner.login,
        ownerAvatarUrl = owner.avatarUrl,
        ownerHtmlUrl = owner.htmlUrl,
        description = description,
        htmlUrl = htmlUrl,
        isPrivate = isPrivate,
        isFork = isFork,
        isArchived = isArchived,
        defaultBranch = defaultBranch,
        language = language,
        stargazersCount = stargazersCount,
        forksCount = forksCount,
        openIssuesCount = openIssuesCount,
        updatedAt = updatedAt,
        topicsCsv = topics.joinToString(SEPARATOR),
        visibility = visibility.name,
        sortBucket = bucket,
        page = page,
    )

fun StarredEntity.toDomain(): Repository =
    Repository(
        id = id,
        name = name,
        fullName = fullName,
        owner =
            RepoOwner(
                login = ownerLogin,
                avatarUrl = ownerAvatarUrl,
                htmlUrl = ownerHtmlUrl,
            ),
        description = description,
        htmlUrl = htmlUrl,
        isPrivate = isPrivate,
        isFork = isFork,
        isArchived = isArchived,
        defaultBranch = defaultBranch,
        language = language,
        stargazersCount = stargazersCount,
        forksCount = forksCount,
        openIssuesCount = openIssuesCount,
        updatedAt = updatedAt,
        topics = topicsCsv.takeIf { it.isNotBlank() }?.split(SEPARATOR).orEmpty(),
        visibility = runCatching { RepoVisibility.valueOf(visibility) }.getOrDefault(RepoVisibility.PUBLIC),
        isStarred = true,
    )

private const val SEPARATOR = ","
