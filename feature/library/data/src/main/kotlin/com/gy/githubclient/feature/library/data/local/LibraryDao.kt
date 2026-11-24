/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.library.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
    @Query("SELECT * FROM tracked_apps ORDER BY addedAt ASC, displayName ASC")
    fun observeAll(): Flow<List<TrackedAppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(app: TrackedAppEntity)

    @Query("DELETE FROM tracked_apps WHERE fullName = :fullName")
    suspend fun remove(fullName: String)
}
