/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DiscoverItemEntity::class], version = 1, exportSchema = false)
abstract class DiscoverDatabase : RoomDatabase() {
    abstract fun discoverDao(): DiscoverDao

    companion object {
        const val NAME = "discover.db"
    }
}
