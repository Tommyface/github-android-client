/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DiscoverDao {
    @Query("SELECT * FROM discover_items ORDER BY category ASC, name ASC")
    fun observeAll(): Flow<List<DiscoverItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DiscoverItemEntity>)

    @Query("DELETE FROM discover_items")
    suspend fun clearAll()
}
