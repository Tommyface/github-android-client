/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DownloadTaskEntity::class], version = 2, exportSchema = true)
abstract class DownloadDatabase : RoomDatabase() {
    abstract fun downloadDao(): DownloadDao

    companion object {
        const val NAME = "downloads.db"
    }
}
