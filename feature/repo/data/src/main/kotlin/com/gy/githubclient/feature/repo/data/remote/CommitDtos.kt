/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data.remote

import com.gy.githubclient.feature.repo.domain.model.CommitDetail
import com.gy.githubclient.feature.repo.domain.model.CommitFile
import com.gy.githubclient.feature.repo.domain.model.CommitSummary
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** commit.author 内嵌对象。 */
@Serializable
data class GitAuthorDto(
    val name: String? = null,
    val date: String? = null,
)

/** commit 对象（列表与详情共用）。 */
@Serializable
data class CommitPayloadDto(
    val message: String = "",
    val author: GitAuthorDto = GitAuthorDto(),
)

/** 提交列表项。 */
@Serializable
data class CommitItemDto(
    val sha: String = "",
    val commit: CommitPayloadDto = CommitPayloadDto(),
    @SerialName("html_url") val htmlUrl: String = "",
)

/** 单次提交详情里的变更文件。 */
@Serializable
data class CommitFileDto(
    val filename: String = "",
    val status: String = "",
    val additions: Int = 0,
    val deletions: Int = 0,
    val patch: String? = null,
)

/** 提交详情：在列表项基础上多 files 数组。 */
@Serializable
data class CommitDetailDto(
    val sha: String = "",
    val commit: CommitPayloadDto = CommitPayloadDto(),
    @SerialName("html_url") val htmlUrl: String = "",
    val files: List<CommitFileDto> = emptyList(),
)

fun CommitItemDto.toSummary(): CommitSummary =
    CommitSummary(
        sha = sha,
        message = commit.message,
        authorName = commit.author.name,
        authorDate = commit.author.date,
        htmlUrl = htmlUrl,
    )

fun CommitDetailDto.toDetail(): CommitDetail =
    CommitDetail(
        sha = sha,
        message = commit.message,
        authorName = commit.author.name,
        authorDate = commit.author.date,
        htmlUrl = htmlUrl,
        files =
            files.map {
                CommitFile(
                    filename = it.filename,
                    status = it.status,
                    additions = it.additions,
                    deletions = it.deletions,
                    patch = it.patch,
                )
            },
    )
