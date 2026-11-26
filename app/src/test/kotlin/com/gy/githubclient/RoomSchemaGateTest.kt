/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 门禁 8：Room 数据库结构安全（M0-10）。
 *
 * 背景：所有 Room 库都配置了 `fallbackToDestructiveMigration()`。
 * 一旦有人递增了 @Database 的 version 却忘了写 Migration，
 * 用户的历史数据（仓库缓存、下载任务、收藏、应用库）会被**静默清空**——
 * 这类故障不崩溃、不报错，比崩溃更难发现，因此必须有机器门禁。
 *
 * 规则：
 *  R1 每个 @Database 必须显式声明 exportSchema（为后续迁移测试留基础）；
 *  R2 version ≥ 2 的库，其所属模块必须存在 androidx.room.migration.Migration 实现；
 *  R3 version ≥ 2 的库必须已在 Room 构造处注册迁移（.addMigrations(...)）。
 *
 * 说明：真正的 MigrationTestHelper 迁移测试需要历史版本 schema JSON，
 * 该资产在首次启用 schema 导出后于下一次 schema 变更时补齐；
 * 本门禁先行杜绝"改版本不写迁移"这一最高危失误。
 */
class RoomSchemaGateTest {
    /**
     * 仓库根目录。Gradle 单测的工作目录是**模块目录**（app/），
     * 直接用 user.dir 会让相对路径全部落空——门禁会"假通过"。
     */
    private val repoRoot: File =
        run {
            var dir = File(System.getProperty("user.dir")).absoluteFile
            while (dir.parentFile != null) {
                if (File(dir, "settings.gradle.kts").exists()) {
                    break
                }
                dir = dir.parentFile
            }
            dir
        }

    private data class DatabaseSpec(
        val moduleDir: String,
        val file: File,
        val version: Int,
        val exportSchema: Boolean,
    )

    private fun collectDatabases(): List<DatabaseSpec> {
        val result = mutableListOf<DatabaseSpec>()
        val featureDir = File(repoRoot, "feature")
        if (!featureDir.exists()) return result
        featureDir.listFiles()?.forEach { feature ->
            val dataDir = File(feature, "data")
            if (!dataDir.exists()) return@forEach
            dataDir
                .walkTopDown()
                .filter { it.isFile && it.name.endsWith("Database.kt") }
                .forEach { file ->
                    val text = file.readText()
                    val version =
                        Regex("""version\s*=\s*(\d+)""")
                            .find(text)
                            ?.groupValues
                            ?.get(1)
                            ?.toIntOrNull()
                            ?: Regex("""const val VERSION\s*=\s*(\d+)""")
                                .find(text)
                                ?.groupValues
                                ?.get(1)
                                ?.toIntOrNull()
                            ?: return@forEach
                    val exportSchema =
                        Regex("""exportSchema\s*=\s*(true|false)""")
                            .find(text)
                            ?.groupValues
                            ?.get(1)
                            ?.toBoolean() ?: false
                    result +=
                        DatabaseSpec(
                            moduleDir = dataDir.path,
                            file = file,
                            version = version,
                            exportSchema = exportSchema,
                        )
                }
        }
        return result
    }

    @Test
    fun everyRoomDatabaseDeclaresExportSchema() {
        val databases = collectDatabases()
        assertTrue("未发现任何 Room 数据库，门禁可能失效", databases.isNotEmpty())
        databases.forEach { db ->
            assertTrue(
                "${db.file.name} 必须显式声明 exportSchema（当前 exportSchema=${db.exportSchema}）",
                db.exportSchema,
            )
        }
    }

    @Test
    fun databasesAtVersionTwoOrAboveDeclareMigrations() {
        val databases = collectDatabases()
        assertTrue("未发现任何 Room 数据库，门禁可能失效", databases.isNotEmpty())
        databases.filter { it.version >= 2 }.forEach { db ->
            val moduleFiles =
                File(db.moduleDir).walkTopDown().filter { it.isFile && it.name.endsWith(".kt") }.toList()
            val hasMigration =
                moduleFiles.any { file ->
                    file.readText().contains("androidx.room.migration.Migration")
                }
            assertTrue(
                "${db.file.name} 的 version=${db.version} ≥ 2，但所属模块没有任何 Migration 实现。" +
                    "缺少迁移会在运行时静默清空用户数据。",
                hasMigration,
            )

            val registered =
                moduleFiles.any { file ->
                    file.readText().contains(".addMigrations(")
                }
            assertTrue(
                "${db.file.name} 的 version=${db.version} ≥ 2，但 Room 构造处没有 .addMigrations(...) 注册。",
                registered,
            )
        }
    }
}
