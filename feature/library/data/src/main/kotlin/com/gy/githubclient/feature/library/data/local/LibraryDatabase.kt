/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.library.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [TrackedAppEntity::class], version = 1, exportSchema = true)
abstract class LibraryDatabase : RoomDatabase() {
    abstract fun libraryDao(): LibraryDao

    companion object {
        const val NAME = "library.db"
    }
}
