/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.library.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gy.githubclient.feature.library.domain.model.TrackedApp

@Entity(tableName = "tracked_apps")
data class TrackedAppEntity(
    @PrimaryKey val fullName: String,
    val owner: String,
    val name: String,
    val displayName: String,
    val installedVersion: String,
    val abi: String,
    val addedAt: String,
)

fun TrackedApp.toEntity(): TrackedAppEntity =
    TrackedAppEntity(
        fullName = fullName,
        owner = owner,
        name = name,
        displayName = displayName,
        installedVersion = installedVersion,
        abi = abi,
        addedAt = addedAt,
    )

fun TrackedAppEntity.toDomain(): TrackedApp =
    TrackedApp(
        owner = owner,
        name = name,
        displayName = displayName,
        installedVersion = installedVersion,
        abi = abi,
        addedAt = addedAt,
    )
