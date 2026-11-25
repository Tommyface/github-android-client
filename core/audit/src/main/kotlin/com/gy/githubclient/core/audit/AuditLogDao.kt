/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.audit

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** 审计表读写：只追加、按时间倒序查看、可清空与裁剪。 */
@Dao
interface AuditLogDao {
    @Insert
    suspend fun insert(entity: AuditLogEntity): Long

    @Query("SELECT * FROM audit_log ORDER BY id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<AuditLogEntity>>

    @Query("DELETE FROM audit_log")
    suspend fun clear()

    /** 只保留最近 [keep] 条，防止审计表无限增长。 */
    @Query("DELETE FROM audit_log WHERE id NOT IN (SELECT id FROM audit_log ORDER BY id DESC LIMIT :keep)")
    suspend fun trimTo(keep: Int)
}
