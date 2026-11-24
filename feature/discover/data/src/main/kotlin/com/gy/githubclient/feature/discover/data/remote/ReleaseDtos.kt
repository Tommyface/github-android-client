/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.data.remote

import com.gy.githubclient.feature.discover.domain.model.ReleaseAsset
import com.gy.githubclient.feature.discover.domain.model.ReleaseInfo
import com.gy.githubclient.feature.discover.domain.model.TagInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /repos/{owner}/{repo}/releases/latest 与 /releases 共用的资产字段。 */
@Serializable
data class ReleaseAssetDto(
    val id: Long = 0L,
    val name: String = "",
    val size: Long = 0L,
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    @SerialName("content_type") val contentType: String = "",
    @SerialName("download_count") val downloadCount: Long = 0L,
    /** GitHub 2024+ 返回，形如「sha256:abcd…」；旧仓库/代理响应可能缺省。 */
    val digest: String? = null,
)

/** GitHub Release 响应字段（仅映射发现页所需子集）。 */
@Suppress("LongParameterList")
@Serializable
data class ReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val body: String? = null,
    @SerialName("prerelease") val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<ReleaseAssetDto> = emptyList(),
)

fun ReleaseAssetDto.toDomain(): ReleaseAsset =
    ReleaseAsset(
        id = id,
        name = name,
        size = size,
        downloadUrl = browserDownloadUrl,
        contentType = contentType,
        downloadCount = downloadCount,
        // 官方 digest 形如「sha256:…」，仅取算法为 sha256 的部分供下载校验。
        sha256 = digest?.takeIf { it.startsWith("sha256:", ignoreCase = true) }?.substringAfter(':'),
    )

fun ReleaseDto.toDomain(): ReleaseInfo =
    ReleaseInfo(
        tagName = tagName,
        name = name.ifBlank { tagName },
        publishedAt = publishedAt,
        htmlUrl = htmlUrl,
        body = body,
        isPrerelease = prerelease,
        isDraft = draft,
        assets = assets.map { it.toDomain() },
    )

/** GET /repos/{owner}/{repo}/tags 单条（v0.8.0 M14）。 */
@Serializable
data class TagDto(
    val name: String = "",
    val commit: TagCommitDto = TagCommitDto(),
)

@Serializable
data class TagCommitDto(
    val sha: String = "",
)

fun TagDto.toDomain(): TagInfo =
    TagInfo(
        name = name,
        commitSha = commit.sha,
    )
