/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.data.remote

import com.gy.githubclient.feature.profile.domain.model.GistFile
import com.gy.githubclient.feature.profile.domain.model.GistItem
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Gist 内单文件 DTO（v0.15.0 A6）。列表端点无 content，详情端点有。 */
@Serializable
data class GistFileDto(
    val filename: String = "",
    val language: String? = null,
    val type: String? = null,
    val size: Int = 0,
    val content: String? = null,
    @SerialName("raw_url") val rawUrl: String? = null,
    val truncated: Boolean = false,
) {
    fun toDomain(): GistFile =
        GistFile(
            filename = filename,
            language = language,
            type = type,
            size = size,
            content = content,
            rawUrl = rawUrl,
            truncated = truncated,
        )
}

/** Gist 条目 DTO（列表与详情共用，v0.15.0 A6）。 */
@Suppress("LongParameterList")
@Serializable
data class GistDto(
    val id: String = "",
    val description: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val public: Boolean = true,
    val owner: GistOwnerDto? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val files: Map<String, GistFileDto> = emptyMap(),
) {
    fun toDomain(): GistItem =
        GistItem(
            id = id,
            description = description,
            htmlUrl = htmlUrl,
            isPublic = public,
            ownerLogin = owner?.login.orEmpty(),
            createdAt = createdAt,
            updatedAt = updatedAt,
            files = files.values.map { it.toDomain() },
        )
}

/** Gist 所有者最小 DTO。 */
@Serializable
data class GistOwnerDto(
    val login: String = "",
)
