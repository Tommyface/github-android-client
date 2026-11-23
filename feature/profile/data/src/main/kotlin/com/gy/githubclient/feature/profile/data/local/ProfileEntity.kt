/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 用户画像缓存：单行固定主键，刷新即覆盖。 */
@Suppress("LongParameterList")
@Entity(tableName = "user_profile")
data class ProfileEntity(
    @PrimaryKey val id: Long,
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
