/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface RepoDao {
    /** 观察指定 bucket 的缓存列表，按更新时间和分页顺序排列。 */
    @Query("SELECT * FROM repositories WHERE bucket = :bucket ORDER BY page ASC, updatedAt DESC")
    fun observeByBucket(bucket: String): Flow<List<RepoEntity>>

    @Query("SELECT * FROM repositories WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RepoEntity?

    @Query("SELECT * FROM repositories WHERE fullName = :fullName LIMIT 1")
    suspend fun findByFullName(fullName: String): RepoEntity?

    @Query("SELECT etag FROM repo_list_meta WHERE bucket = :bucket")
    suspend fun getEtag(bucket: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<RepoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setEtag(meta: RepoListMetaEntity)

    /** 刷新第一页：先清该 bucket，再写入，保证无陈旧残留。 */
    @Transaction
    suspend fun replaceBucket(
        bucket: String,
        items: List<RepoEntity>,
        etag: String?,
    ) {
        clearBucket(bucket)
        upsertAll(items)
        if (etag != null) setEtag(RepoListMetaEntity(bucket, etag))
    }

    /** 追加下一页：只 upsert，不动第一页 ETag。 */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun appendAll(items: List<RepoEntity>)

    @Query("DELETE FROM repositories WHERE bucket = :bucket")
    suspend fun clearBucket(bucket: String)

    @Query("UPDATE repositories SET isStarred = :starred WHERE id = :id")
    suspend fun updateStarred(
        id: Long,
        starred: Boolean,
    )

    @Query("DELETE FROM repositories")
    suspend fun clearAll()

    @Query("DELETE FROM repo_list_meta")
    suspend fun clearAllMeta()
}
