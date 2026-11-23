/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.starred.data.remote

import com.gy.githubclient.feature.repo.domain.model.RepoOwner
import com.gy.githubclient.feature.repo.domain.model.RepoVisibility
import com.gy.githubclient.feature.repo.domain.model.Repository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StarredOwnerDto(
    val login: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
)

/** GET /user/starred 返回的仓库对象（精简字段）。 */
@Suppress("LongParameterList")
@Serializable
data class StarredRepoDto(
    val id: Long = 0L,
    val name: String = "",
    @SerialName("full_name") val fullName: String = "",
    val owner: StarredOwnerDto = StarredOwnerDto(),
    val description: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val private: Boolean = false,
    val fork: Boolean = false,
    val archived: Boolean = false,
    @SerialName("default_branch") val defaultBranch: String = "main",
    val language: String? = null,
    @SerialName("stargazers_count") val stargazersCount: Int = 0,
    @SerialName("forks_count") val forksCount: Int = 0,
    @SerialName("open_issues_count") val openIssuesCount: Int = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
    val topics: List<String> = emptyList(),
    val visibility: String? = null,
    @SerialName("starred") val starred: Boolean? = null,
)

fun StarredRepoDto.toDomain(): Repository =
    Repository(
        id = id,
        name = name,
        fullName = fullName,
        owner =
            RepoOwner(
                login = owner.login,
                avatarUrl = owner.avatarUrl,
                htmlUrl = owner.htmlUrl,
            ),
        description = description,
        htmlUrl = htmlUrl,
        isPrivate = private,
        isFork = fork,
        isArchived = archived,
        defaultBranch = defaultBranch,
        language = language,
        stargazersCount = stargazersCount,
        forksCount = forksCount,
        openIssuesCount = openIssuesCount,
        updatedAt = updatedAt,
        topics = topics,
        visibility = visibility.toVisibility(private),
        isStarred = starred ?: true,
    )

private fun String?.toVisibility(fallbackPrivate: Boolean): RepoVisibility =
    when (this?.lowercase()) {
        "public" -> RepoVisibility.PUBLIC
        "private" -> RepoVisibility.PRIVATE
        "internal" -> RepoVisibility.INTERNAL
        else -> if (fallbackPrivate) RepoVisibility.PRIVATE else RepoVisibility.PUBLIC
    }
