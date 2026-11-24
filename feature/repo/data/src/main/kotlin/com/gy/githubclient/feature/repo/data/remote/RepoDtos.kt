/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data.remote

import com.gy.githubclient.feature.repo.domain.model.Branch
import com.gy.githubclient.feature.repo.domain.model.FileContent
import com.gy.githubclient.feature.repo.domain.model.FileEntry
import com.gy.githubclient.feature.repo.domain.model.IssueComment
import com.gy.githubclient.feature.repo.domain.model.IssueDetail
import com.gy.githubclient.feature.repo.domain.model.IssueItem
import com.gy.githubclient.feature.repo.domain.model.LicenseInfo
import com.gy.githubclient.feature.repo.domain.model.Readme
import com.gy.githubclient.feature.repo.domain.model.RepoOwner
import com.gy.githubclient.feature.repo.domain.model.RepoPermission
import com.gy.githubclient.feature.repo.domain.model.RepoVisibility
import com.gy.githubclient.feature.repo.domain.model.Repository
import com.gy.githubclient.feature.repo.domain.model.StarHistory
import com.gy.githubclient.feature.repo.domain.model.StarWeekEntry
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.io.encoding.ExperimentalEncodingApi

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

/** GET /repos/{o}/{r}/issues 列表项（v0.7.0 M11）。pull_request 字段存在即为 PR。 */
@Serializable
data class IssueItemDto(
    val number: Int = 0,
    val title: String = "",
    val state: String = "open",
    val user: OwnerDto? = null,
    val comments: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("pull_request") val pullRequest: kotlinx.serialization.json.JsonElement? = null,
)

fun IssueItemDto.toDomain(): IssueItem =
    IssueItem(
        number = number,
        title = title,
        state = state,
        isPullRequest = pullRequest != null,
        author = user?.login.orEmpty(),
        commentCount = comments,
        createdAt = createdAt,
        htmlUrl = htmlUrl,
    )

/** GET /repos/{o}/{r}/issues/{n} 详情。 */
@Serializable
data class IssueDetailDto(
    val number: Int = 0,
    val title: String = "",
    val state: String = "open",
    val user: OwnerDto? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("pull_request") val pullRequest: kotlinx.serialization.json.JsonElement? = null,
)

fun IssueDetailDto.toDomain(comments: List<IssueComment>): IssueDetail =
    IssueDetail(
        number = number,
        title = title,
        state = state,
        isPullRequest = pullRequest != null,
        author = user?.login.orEmpty(),
        body = body,
        htmlUrl = htmlUrl,
        comments = comments,
    )

/** GET /repos/{o}/{r}/issues/{n}/comments 单条评论。 */
@Serializable
data class IssueCommentDto(
    val user: OwnerDto? = null,
    val body: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

fun IssueCommentDto.toDomain(): IssueComment =
    IssueComment(
        author = user?.login.orEmpty(),
        body = body.orEmpty(),
        createdAt = createdAt,
    )

/**
 * stargazers/history 单周条目（v0.7.0 M10）。
 * 官方语义：week=周起始 Unix 秒；total=该周增量（非累计）；days=周日起的每日增量；最新周在前。
 */
@Serializable
data class StarHistoryWeekDto(
    val week: Long = 0L,
    val total: Int = 0,
    val days: List<Int> = emptyList(),
)

fun List<StarHistoryWeekDto>.toStarHistory(): StarHistory =
    StarHistory.fromWeekly(
        map { entry ->
            StarWeekEntry(
                weekStartEpoch = entry.week,
                weeklyStars = entry.total,
                dailyStars = entry.days,
            )
        },
    )

/**
 * GET /repos/{o}/{r}/contents/{path} 的条目（目录为数组、单文件为对象，共用字段）。
 * v0.9.0 M17。
 */
@Serializable
data class ContentEntryDto(
    val name: String = "",
    val path: String = "",
    val type: String = "",
    val size: Long = 0,
    val sha: String = "",
    @SerialName("download_url") val downloadUrl: String? = null,
    /** 单文件请求时返回（目录数组不含）；base64 编码。 */
    val content: String? = null,
    val encoding: String? = null,
)

fun ContentEntryDto.toFileEntry(): FileEntry =
    FileEntry(
        name = name,
        path = path,
        type = type,
        size = size,
        sha = sha,
        downloadUrl = downloadUrl,
    )

/** 单文件内容 → 领域模型。base64 缺失/非文本/解码失败均标记 isTextUnavailable。 */
@OptIn(ExperimentalEncodingApi::class)
fun ContentEntryDto.toFileContent(): FileContent {
    val rawContent = content
    val decoded =
        if (encoding == "base64" && !rawContent.isNullOrBlank()) {
            runCatching {
                // kotlin.io.encoding.Base64 为纯 Kotlin 实现，JVM 单测与 Android 真机行为一致；
                // GitHub 返回的 content 含换行，先去除空白再按 Default 解码。
                val clean = rawContent.filter { !it.isWhitespace() }
                val bytes =
                    kotlin.io.encoding.Base64.Default
                        .decode(clean)
                String(bytes, Charsets.UTF_8)
            }.getOrNull()
        } else {
            null
        }
    // 粗略二进制判定：含 NUL 或大量替换字符视为不可读文本。
    val readable =
        decoded != null &&
            !decoded.contains('\u0000') &&
            decoded.count { it == '\uFFFD' } * 20 < decoded.length.coerceAtLeast(1)
    return FileContent(
        path = path,
        name = name,
        content = if (readable) decoded else "",
        size = size,
        isTextUnavailable = !readable,
        downloadUrl = downloadUrl,
    )
}

private fun String?.toVisibility(fallbackPrivate: Boolean): RepoVisibility =
    when (this?.lowercase()) {
        "public" -> RepoVisibility.PUBLIC
        "private" -> RepoVisibility.PRIVATE
        "internal" -> RepoVisibility.INTERNAL
        else -> if (fallbackPrivate) RepoVisibility.PRIVATE else RepoVisibility.PUBLIC
    }
