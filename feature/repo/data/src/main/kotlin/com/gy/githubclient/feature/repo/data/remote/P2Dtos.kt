/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data.remote

import com.gy.githubclient.feature.repo.domain.model.Contributor
import com.gy.githubclient.feature.repo.domain.model.RepoTree
import com.gy.githubclient.feature.repo.domain.model.TreeNode
import com.gy.githubclient.feature.repo.domain.model.languageShares
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /git/trees/{ref}?recursive=1 响应 DTO（v0.16.0 P2-a）。 */
@Serializable
data class GitTreeResponseDto(
    val sha: String = "",
    val tree: List<TreeNodeDto> = emptyList(),
    val truncated: Boolean = false,
) {
    fun toDomain(): RepoTree =
        RepoTree(
            nodes =
                tree.map {
                    TreeNode(
                        path = it.path,
                        type = it.type,
                        size = it.size ?: 0L,
                        sha = it.sha,
                    )
                },
            isTruncated = truncated,
        )
}

/** 文件树单节点 DTO；size 仅 blob 有，tree 节点为 null。 */
@Serializable
data class TreeNodeDto(
    val path: String = "",
    val type: String = "",
    val sha: String = "",
    val size: Long? = null,
    val mode: String? = null,
)

/** GET /contributors 单条 DTO（v0.16.0 P2-c）。 */
@Serializable
data class ContributorDto(
    val login: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val contributions: Int = 0,
) {
    fun toDomain(): Contributor =
        Contributor(
            login = login,
            avatarUrl = avatarUrl,
            htmlUrl = htmlUrl,
            contributions = contributions,
        )
}

/**
 * GET /languages 返回 `{ "Kotlin": 806384, ... }`，直接是 Map<String, Long>。
 * 用 kotlinx.serialization 的 MapSerializer 解析后交给领域函数计算占比。
 */
internal fun Map<String, Long>.toLanguageShares() = languageShares(this)
