/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.library.domain.repository

import com.gy.githubclient.feature.library.domain.model.LibraryEntry
import com.gy.githubclient.feature.library.domain.model.TrackedApp
import kotlinx.coroutines.flow.Flow

/** 应用库仓库契约：Room 为追踪列表单一数据源，检查结果为内存态快照。 */
interface LibraryRepository {
    fun observeTracked(): Flow<List<TrackedApp>>

    suspend fun addTracked(app: TrackedApp)

    suspend fun removeTracked(fullName: String)

    /** 批量检查所有追踪项的最新 Release，逐项产出更新状态（单项失败不影响其它）。 */
    suspend fun checkUpdates(apps: List<TrackedApp>): List<LibraryEntry>
}
