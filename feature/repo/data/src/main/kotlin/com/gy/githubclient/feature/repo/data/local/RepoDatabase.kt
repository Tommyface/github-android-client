/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [RepoEntity::class, RepoListMetaEntity::class],
    version = RepoDatabase.VERSION,
    exportSchema = true,
)
abstract class RepoDatabase : RoomDatabase() {
    abstract fun repoDao(): RepoDao

    companion object {
        const val VERSION = 1
        const val NAME = "repo-cache.db"
    }
}
