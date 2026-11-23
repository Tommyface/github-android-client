/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.starred.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [StarredEntity::class],
    version = StarredDatabase.VERSION,
    exportSchema = false,
)
abstract class StarredDatabase : RoomDatabase() {
    abstract fun starredDao(): StarredDao

    companion object {
        const val VERSION = 1
        const val NAME = "starred-cache.db"
    }
}
