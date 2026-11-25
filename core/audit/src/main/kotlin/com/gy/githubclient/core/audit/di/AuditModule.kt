/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.audit.di

import androidx.room.Room
import com.gy.githubclient.core.audit.AuditDatabase
import com.gy.githubclient.core.audit.AuditLogDao
import com.gy.githubclient.core.audit.AuditLogRepository
import com.gy.githubclient.core.audit.RoomAuditLogRepository
import com.gy.githubclient.core.audit.RoomAuditSink
import com.gy.githubclient.core.mutation.AuditSink
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** 审计模块 DI：Room 数据库、写入 sink 与查询仓库。 */
val auditModule =
    module {
        single<AuditDatabase> {
            Room
                .databaseBuilder(androidContext(), AuditDatabase::class.java, AuditDatabase.NAME)
                .fallbackToDestructiveMigration()
                .build()
        }

        single<AuditLogDao> { get<AuditDatabase>().auditLogDao() }

        single<AuditSink> { RoomAuditSink(get()) }

        single<AuditLogRepository> { RoomAuditLogRepository(get()) }
    }
