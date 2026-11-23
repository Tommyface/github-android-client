/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.starred.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 星标仓库缓存表：扁平化存储，完整对象由领域模型重建。 */
@Suppress("LongParameterList")
@Entity(tableName = "starred_repos")
data class StarredEntity(
    @PrimaryKey val id: Long,
    val name: String,
    val fullName: String,
    val ownerLogin: String,
    val ownerAvatarUrl: String?,
    val ownerHtmlUrl: String,
    val description: String?,
    val htmlUrl: String,
    val isPrivate: Boolean,
    val isFork: Boolean,
    val isArchived: Boolean,
    val defaultBranch: String,
    val language: String?,
    val stargazersCount: Int,
    val forksCount: Int,
    val openIssuesCount: Int,
    val updatedAt: String?,
    val topicsCsv: String,
    val visibility: String,
    val sortBucket: String,
    val page: Int,
)
