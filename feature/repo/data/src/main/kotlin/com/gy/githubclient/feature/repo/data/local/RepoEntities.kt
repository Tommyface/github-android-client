/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 仓库缓存表。复合字段（topics / 权限 / 许可证）以管道分隔或扁平布尔存储，
 * 避免引入额外 TypeConverter 的序列化开销；完整对象以领域模型重建。
 */
@Entity(tableName = "repositories")
@Suppress("LongParameterList")
data class RepoEntity(
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
    /** 逗号分隔的 topics。 */
    val topicsCsv: String,
    val visibility: String,
    val licenseKey: String?,
    val licenseName: String?,
    val permAdmin: Boolean,
    val permMaintain: Boolean,
    val permPush: Boolean,
    val permTriage: Boolean,
    val permPull: Boolean,
    val isStarred: Boolean,
    /** 列表归属维度 + 页码，用于按筛选条件回放缓存。 */
    val bucket: String,
    val page: Int,
)

/** 每个列表 bucket 的条件请求 ETag，304 复用缓存不消耗额度。 */
@Entity(tableName = "repo_list_meta")
data class RepoListMetaEntity(
    @PrimaryKey val bucket: String,
    val etag: String,
)
