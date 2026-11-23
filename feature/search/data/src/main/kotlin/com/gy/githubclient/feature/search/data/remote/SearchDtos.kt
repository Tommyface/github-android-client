/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.search.data.remote

import com.gy.githubclient.feature.search.domain.model.CodeHit
import com.gy.githubclient.feature.search.domain.model.CommitHit
import com.gy.githubclient.feature.search.domain.model.IssueHit
import com.gy.githubclient.feature.search.domain.model.RepoHit
import com.gy.githubclient.feature.search.domain.model.UserHit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** 搜索响应统一外壳。 */
@Serializable
data class SearchResponseDto<T>(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("incomplete_results") val incompleteResults: Boolean = false,
    val items: List<T> = emptyList(),
)

@Serializable
data class OwnerMiniDto(
    val login: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
)

@Serializable
data class RepoSearchItemDto(
    val id: Long = 0L,
    val name: String = "",
    @SerialName("full_name") val fullName: String = "",
    val owner: OwnerMiniDto = OwnerMiniDto(),
    val description: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val `private`: Boolean = false,
    val language: String? = null,
    @SerialName("stargazers_count") val stargazersCount: Int = 0,
    @SerialName("forks_count") val forksCount: Int = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class RepoNestedDto(
    @SerialName("full_name") val fullName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
)

@Serializable
data class CodeSearchItemDto(
    val name: String = "",
    val path: String = "",
    val sha: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val repository: RepoNestedDto = RepoNestedDto(),
)

@Serializable
data class PullRequestMarkerDto(
    @SerialName("html_url") val htmlUrl: String? = null,
)

@Serializable
data class IssueSearchItemDto(
    val id: Long = 0L,
    val number: Int = 0,
    val title: String = "",
    val state: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val comments: Int = 0,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("pull_request") val pullRequest: PullRequestMarkerDto? = null,
    @SerialName("repository_url") val repositoryUrl: String? = null,
)

@Serializable
data class UserSearchItemDto(
    val id: Long = 0L,
    val login: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val type: String = "User",
)

@Serializable
data class CommitAuthorDto(
    val name: String? = null,
)

@Serializable
data class CommitDetailDto(
    val message: String = "",
    val author: CommitAuthorDto = CommitAuthorDto(),
)

@Serializable
data class CommitSearchItemDto(
    val sha: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val commit: CommitDetailDto = CommitDetailDto(),
    val repository: RepoNestedDto = RepoNestedDto(),
)

fun RepoSearchItemDto.toHit(): RepoHit =
    RepoHit(
        id = id.toString(),
        title = fullName.ifBlank { name },
        htmlUrl = htmlUrl,
        fullName = fullName,
        description = description,
        ownerLogin = owner.login,
        ownerAvatarUrl = owner.avatarUrl,
        language = language,
        stargazersCount = stargazersCount,
        forksCount = forksCount,
        isPrivate = `private`,
        updatedAt = updatedAt,
    )

fun CodeSearchItemDto.toHit(): CodeHit =
    CodeHit(
        id = sha.ifBlank { htmlUrl },
        title = name,
        htmlUrl = htmlUrl,
        path = path,
        repositoryFullName = repository.fullName,
        repositoryHtmlUrl = repository.htmlUrl,
    )

fun IssueSearchItemDto.toHit(): IssueHit =
    IssueHit(
        id = id.toString(),
        title = title,
        htmlUrl = htmlUrl,
        number = number,
        state = state,
        isPullRequest = pullRequest != null,
        repositoryFullName = repositoryUrl?.substringAfterLast('/') ?: "",
        comments = comments,
        updatedAt = updatedAt,
    )

fun UserSearchItemDto.toHit(): UserHit =
    UserHit(
        id = id.toString(),
        title = login,
        htmlUrl = htmlUrl,
        login = login,
        avatarUrl = avatarUrl,
        accountType = type,
    )

fun CommitSearchItemDto.toHit(): CommitHit =
    CommitHit(
        id = sha,
        title =
            commit.message
                .lineSequence()
                .firstOrNull()
                .orEmpty()
                .ifBlank { sha.take(SHORT_SHA) },
        htmlUrl = htmlUrl,
        sha = sha,
        authorName = commit.author.name,
        repositoryFullName = repository.fullName,
    )

private const val SHORT_SHA = 7
