/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.starred.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface StarredDao {
    @Query("SELECT * FROM starred_repos ORDER BY page ASC, updatedAt DESC")
    fun observeAll(): Flow<List<StarredEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<StarredEntity>)

    /** 刷新第一页：先清该排序桶，再写入。 */
    @Transaction
    suspend fun replaceBucket(
        bucket: String,
        items: List<StarredEntity>,
    ) {
        clearBucket(bucket)
        upsertAll(items)
    }

    @Query("DELETE FROM starred_repos WHERE sortBucket = :bucket")
    suspend fun clearBucket(bucket: String)

    @Query("DELETE FROM starred_repos WHERE fullName = :fullName")
    suspend fun deleteByFullName(fullName: String)

    @Query("DELETE FROM starred_repos")
    suspend fun clearAll()
}
