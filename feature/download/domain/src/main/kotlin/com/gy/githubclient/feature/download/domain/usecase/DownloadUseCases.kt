/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.domain.usecase

import com.gy.githubclient.feature.download.domain.model.DownloadRequest
import com.gy.githubclient.feature.download.domain.model.DownloadStats
import com.gy.githubclient.feature.download.domain.model.DownloadTask
import com.gy.githubclient.feature.download.domain.repository.DownloadManager
import kotlinx.coroutines.flow.Flow

/** 观察下载任务列表。 */
class ObserveDownloadsUseCase(
    private val manager: DownloadManager,
) {
    operator fun invoke(): Flow<List<DownloadTask>> = manager.observeTasks()
}

/** 观察下载统计。 */
class ObserveDownloadStatsUseCase(
    private val manager: DownloadManager,
) {
    operator fun invoke(): Flow<DownloadStats> = manager.observeStats()
}

/** 提交下载请求。 */
class EnqueueDownloadUseCase(
    private val manager: DownloadManager,
) {
    suspend operator fun invoke(request: DownloadRequest): String = manager.enqueue(request)
}

/** 取消下载。 */
class CancelDownloadUseCase(
    private val manager: DownloadManager,
) {
    suspend operator fun invoke(id: String) = manager.cancel(id)
}

/** 重试失败任务。 */
class RetryDownloadUseCase(
    private val manager: DownloadManager,
) {
    suspend operator fun invoke(id: String) = manager.retry(id)
}

/** 删除任务记录。 */
class RemoveDownloadUseCase(
    private val manager: DownloadManager,
) {
    suspend operator fun invoke(id: String) = manager.remove(id)
}

/** 清空已完成记录。 */
class ClearCompletedDownloadsUseCase(
    private val manager: DownloadManager,
) {
    suspend operator fun invoke() = manager.clearCompleted()
}
