/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadTaskEntity>>

    @Query("SELECT * FROM download_tasks WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): DownloadTaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(task: DownloadTaskEntity)

    @Query("UPDATE download_tasks SET status = :status, error = :error, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateStatus(
        id: String,
        status: String,
        error: String?,
        updatedAt: String,
    )

    @Query(
        "UPDATE download_tasks SET bytesDownloaded = :bytes, totalBytesActual = :total, percent = :percent, status = :status, updatedAt = :updatedAt WHERE id = :id",
    )
    @Suppress("LongParameterList")
    suspend fun updateProgress(
        id: String,
        bytes: Long,
        total: Long?,
        percent: Int?,
        status: String,
        updatedAt: String,
    )

    @Query("UPDATE download_tasks SET filePath = :path, status = :status, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateComplete(
        id: String,
        path: String,
        status: String,
        updatedAt: String,
    )

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM download_tasks WHERE status IN ('DONE','FAILED','CANCELLED')")
    suspend fun clearCompleted()

    @Query("SELECT fileName FROM download_tasks")
    suspend fun allFileNames(): List<String>
}
