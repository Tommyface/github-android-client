/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.audit

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** 写操作审计表：每次写操作一条，含重试次数与 GitHub 请求 id。 */
@Entity(tableName = "audit_log")
data class AuditLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val action: String,
    val target: String,
    val success: Boolean,
    @ColumnInfo(name = "status_code") val statusCode: Int? = null,
    val attempts: Int = 1,
    @ColumnInfo(name = "request_id") val requestId: String? = null,
    val message: String? = null,
    @ColumnInfo(name = "timestamp_millis") val timestampMillis: Long,
)

/** 审计记录的只读视图。 */
data class AuditRecord(
    val id: Long,
    val action: String,
    val target: String,
    val success: Boolean,
    val statusCode: Int?,
    val attempts: Int,
    val requestId: String?,
    val message: String?,
    val timestampMillis: Long,
)

internal fun AuditLogEntity.toRecord(): AuditRecord =
    AuditRecord(
        id = id,
        action = action,
        target = target,
        success = success,
        statusCode = statusCode,
        attempts = attempts,
        requestId = requestId,
        message = message,
        timestampMillis = timestampMillis,
    )
