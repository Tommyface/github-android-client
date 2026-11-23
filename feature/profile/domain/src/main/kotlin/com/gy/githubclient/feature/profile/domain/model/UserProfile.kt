/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.domain.model

/**
 * 当前登录用户画像，对应 GET /user 的最小完备视图。
 * 作为「我的」页的单一数据源，离线时回退到本地 Room 缓存。
 */
@Suppress("LongParameterList")
data class UserProfile(
    val id: Long,
    val login: String,
    val name: String?,
    val avatarUrl: String?,
    val htmlUrl: String,
    val bio: String?,
    val company: String?,
    val location: String?,
    val blog: String?,
    val email: String?,
    val publicRepos: Int,
    val publicGists: Int,
    val followers: Int,
    val following: Int,
    val createdAt: String?,
)
