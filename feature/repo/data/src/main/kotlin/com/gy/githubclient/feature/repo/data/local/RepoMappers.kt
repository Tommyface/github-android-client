/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data.local

import com.gy.githubclient.feature.repo.domain.model.LicenseInfo
import com.gy.githubclient.feature.repo.domain.model.RepoOwner
import com.gy.githubclient.feature.repo.domain.model.RepoPermission
import com.gy.githubclient.feature.repo.domain.model.RepoVisibility
import com.gy.githubclient.feature.repo.domain.model.Repository

fun Repository.toEntity(
    bucket: String,
    page: Int,
): RepoEntity =
    RepoEntity(
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
        licenseKey = license?.key,
        licenseName = license?.name,
        permAdmin = permission?.admin ?: false,
        permMaintain = permission?.maintain ?: false,
        permPush = permission?.push ?: false,
        permTriage = permission?.triage ?: false,
        permPull = permission?.pull ?: false,
        isStarred = isStarred,
        bucket = bucket,
        page = page,
    )

fun RepoEntity.toDomain(): Repository =
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
        license =
            if (licenseKey != null || licenseName != null) {
                LicenseInfo(licenseKey, licenseName)
            } else {
                null
            },
        permission =
            RepoPermission(
                admin = permAdmin,
                maintain = permMaintain,
                push = permPush,
                triage = permTriage,
                pull = permPull,
            ),
        isStarred = isStarred,
    )

private const val SEPARATOR = ","
