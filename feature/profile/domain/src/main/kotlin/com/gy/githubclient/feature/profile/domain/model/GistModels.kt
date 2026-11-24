/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.domain.model

/** Gist 内单个文件（v0.15.0 A6）：[content] 可能为空（列表端点通常不返回正文）。 */
data class GistFile(
    val filename: String,
    val language: String?,
    val type: String?,
    val size: Int,
    val content: String?,
    val rawUrl: String?,
    val truncated: Boolean = false,
) {
    val hasContent: Boolean get() = !content.isNullOrEmpty()
}

/**
 * Gist 条目（v0.15.0 A6）：列表端点 GET /users/{login}/gists 与详情 GET /gists/{id}
 * 共用同一视图；列表的 files 含元信息但通常无正文，详情的 files 含 content。
 */
@Suppress("LongParameterList")
data class GistItem(
    val id: String,
    val description: String?,
    val htmlUrl: String,
    val isPublic: Boolean,
    val ownerLogin: String,
    val createdAt: String?,
    val updatedAt: String?,
    val files: List<GistFile>,
) {
    /** 首个有正文的文件；用于详情页默认展示。 */
    val primaryFile: GistFile? get() = files.firstOrNull { it.hasContent } ?: files.firstOrNull()

    /** 主语言：取首个文件的 language，供列表标签展示。 */
    val primaryLanguage: String? get() = files.firstOrNull()?.language
}
