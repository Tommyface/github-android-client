/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data.remote

import com.gy.githubclient.feature.repo.domain.model.Branch
import com.gy.githubclient.feature.repo.domain.model.LicenseInfo
import com.gy.githubclient.feature.repo.domain.model.Readme
import com.gy.githubclient.feature.repo.domain.model.RepoOwner
import com.gy.githubclient.feature.repo.domain.model.RepoPermission
import com.gy.githubclient.feature.repo.domain.model.RepoVisibility
import com.gy.githubclient.feature.repo.domain.model.Repository
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class OwnerDto(
    val login: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
)

@Serializable
data class PermissionsDto(
    val admin: Boolean = false,
    val maintain: Boolean = false,
    val push: Boolean = false,
    val triage: Boolean = false,
    val pull: Boolean = false,
)

@Serializable
data class LicenseDto(
    val key: String? = null,
    val name: String? = null,
)

/** GET /user/repos 与 GET /repos/{owner}/{repo} 共用的仓库字段。 */
@Suppress("LongParameterList")
@Serializable
data class RepositoryDto(
    val id: Long = 0L,
    val name: String = "",
    @SerialName("full_name") val fullName: String = "",
    val owner: OwnerDto = OwnerDto(),
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
    val license: LicenseDto? = null,
    val permissions: PermissionsDto? = null,
    /** GET /user/starred 列表不含该字段；详情/列表由调用方决定是否置位。 */
    @SerialName("starred") val starred: Boolean? = null,
)

@Serializable
data class BranchDto(
    val name: String = "",
    val sha: String = "",
    val protected: Boolean = false,
)

fun RepositoryDto.toDomain(): Repository =
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
        license = license?.let { LicenseInfo(it.key, it.name) },
        permission =
            permissions?.let {
                RepoPermission(
                    admin = it.admin,
                    maintain = it.maintain,
                    push = it.push,
                    triage = it.triage,
                    pull = it.pull,
                )
            },
        isStarred = starred ?: false,
    )

fun BranchDto.toDomain(): Branch =
    Branch(
        name = name,
        sha = sha,
        isProtected = protected,
    )

/** GET /repos/{owner}/{repo}/readme 响应（v0.6.0 M6）。 */
@Serializable
data class ReadmeDto(
    val name: String = "",
    val path: String = "",
    /** base64 编码的 README 内容（GitHub 统一以 base64 返回）。 */
    val content: String = "",
    val encoding: String = "base64",
    val size: Long = 0,
)

fun ReadmeDto.toDomain(): Readme? {
    if (encoding != "base64") return null
    // android.util.Base64 而非 java.util.Base64：后者需 API 26 / desugaring（minSdk 24）；DEFAULT 模式自动忽略换行。
    val decoded =
        runCatching {
            String(android.util.Base64.decode(content, android.util.Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull() ?: return null
    return Readme(
        fileName = name.ifBlank { path.substringAfterLast('/') },
        content = decoded,
    )
}

private fun String?.toVisibility(fallbackPrivate: Boolean): RepoVisibility =
    when (this?.lowercase()) {
        "public" -> RepoVisibility.PUBLIC
        "private" -> RepoVisibility.PRIVATE
        "internal" -> RepoVisibility.INTERNAL
        else -> if (fallbackPrivate) RepoVisibility.PRIVATE else RepoVisibility.PUBLIC
    }
