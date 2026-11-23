/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.domain.repository

import com.gy.githubclient.feature.download.domain.model.DownloadRequest
import com.gy.githubclient.feature.download.domain.model.DownloadStats
import com.gy.githubclient.feature.download.domain.model.DownloadTask
import kotlinx.coroutines.flow.Flow

/**
 * 下载任务管理契约。
 * 负责把下载请求持久化、入队到 WorkManager、并对外暴露可观察的任务列表。
 * 真正的网络传输由 core 的 Downloader 实现，本契约是任务生命周期的门面。
 */
interface DownloadManager {
    fun observeTasks(): Flow<List<DownloadTask>>

    fun observeStats(): Flow<DownloadStats>

    suspend fun getTask(id: String): DownloadTask?

    /** 提交下载请求：落库 + 入队后台任务，返回任务 id。 */
    suspend fun enqueue(request: DownloadRequest): String

    suspend fun cancel(id: String)

    suspend fun retry(id: String)

    suspend fun remove(id: String)

    suspend fun clearCompleted()
}
