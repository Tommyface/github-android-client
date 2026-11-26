/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 下载库迁移。
 *
 * v1 → v2：新增 `expectedSha256` 列（记录官方 digest，用于下载完成后的完整性校验）。
 * 旧行该列为 NULL，表示"未校验"，与领域模型 `expectedSha256: String? = null` 语义一致。
 *
 * 约定：任何一次 schema 变更都必须
 *  1. 递增 @Database 的 version；
 *  2. 在此文件新增一条 Migration；
 *  3. 在 `DownloadDataModule` 中注册；
 *  4. 由门禁 `RoomSchemaGateTest` 校验（version ≥ 2 必须存在 Migration）。
 *
 * 没有第 2、3 步时，`fallbackToDestructiveMigration` 会静默清空用户的下载历史——
 * 这类"数据悄悄消失"的问题比崩溃更难发现，因此门禁对此零容忍。
 */
val MIGRATION_1_2: Migration =
    object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `download_tasks` ADD COLUMN `expectedSha256` TEXT")
        }
    }
