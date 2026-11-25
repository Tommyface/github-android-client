/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.audit

import androidx.room.Database
import androidx.room.RoomDatabase

/** 审计数据库：独立库，避免与业务缓存互相影响。 */
@Database(entities = [AuditLogEntity::class], version = 1, exportSchema = false)
abstract class AuditDatabase : RoomDatabase() {
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        const val NAME = "audit.db"
    }
}
