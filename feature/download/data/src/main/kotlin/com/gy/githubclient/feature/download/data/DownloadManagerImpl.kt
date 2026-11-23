/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.data

import android.content.Context
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.gy.githubclient.feature.download.data.local.DownloadDao
import com.gy.githubclient.feature.download.data.local.toDomain
import com.gy.githubclient.feature.download.data.local.toEntity
import com.gy.githubclient.feature.download.data.worker.DownloadWorker
import com.gy.githubclient.feature.download.domain.model.DownloadRequest
import com.gy.githubclient.feature.download.domain.model.DownloadStats
import com.gy.githubclient.feature.download.domain.model.DownloadStatus
import com.gy.githubclient.feature.download.domain.model.DownloadTask
import com.gy.githubclient.feature.download.domain.repository.DownloadManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import java.util.UUID

/** 下载任务管理门面：Room 持久化 + WorkManager 入队。 */
class DownloadManagerImpl(
    private val context: Context,
    private val dao: DownloadDao,
) : DownloadManager {
    private val workManager: WorkManager by lazy { WorkManager.getInstance(context) }

    override fun observeTasks(): Flow<List<DownloadTask>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeStats(): Flow<DownloadStats> =
        dao.observeAll().map { list ->
            DownloadStats(
                active = list.count { !it.toDomain().status.isTerminal },
                done = list.count { it.status == DownloadStatus.DONE.name },
                failed = list.count { it.status == DownloadStatus.FAILED.name },
                totalBytesDownloaded = list.sumOf { it.bytesDownloaded },
            )
        }

    override suspend fun getTask(id: String): DownloadTask? = dao.getById(id)?.toDomain()

    override suspend fun enqueue(request: DownloadRequest): String {
        val id = UUID.randomUUID().toString()
        val now = Clock.System.now().toString()
        val task =
            DownloadTask(
                id = id,
                request = request,
                status = DownloadStatus.QUEUED,
                totalBytes = if (request.totalBytes > 0) request.totalBytes else null,
                createdAt = now,
                updatedAt = now,
            )
        dao.upsert(task.toEntity())
        enqueueWork(task)
        return id
    }

    override suspend fun cancel(id: String) {
        if (dao.getById(id) == null) return
        workManager.cancelWorkById(UUID.fromString(id))
        dao.updateStatus(id, DownloadStatus.CANCELLED.name, "用户取消", Clock.System.now().toString())
    }

    override suspend fun retry(id: String) {
        val task = dao.getById(id)?.toDomain() ?: return
        val now = Clock.System.now().toString()
        dao.updateStatus(id, DownloadStatus.QUEUED.name, null, now)
        enqueueWork(task.copy(status = DownloadStatus.QUEUED, error = null, updatedAt = now))
    }

    override suspend fun remove(id: String) {
        workManager.cancelWorkById(UUID.fromString(id))
        dao.delete(id)
    }

    override suspend fun clearCompleted() = dao.clearCompleted()

    private fun enqueueWork(task: DownloadTask) {
        val input =
            Data
                .Builder()
                .putString(DownloadWorker.KEY_TASK_ID, task.id)
                .putString(DownloadWorker.KEY_URL, task.request.url)
                .putString(DownloadWorker.KEY_FILE_NAME, task.request.fileName)
                .putLong(DownloadWorker.KEY_ASSET_ID, task.request.assetId)
                .putLong(DownloadWorker.KEY_TOTAL_BYTES, task.request.totalBytes)
                .build()
        val work =
            OneTimeWorkRequestBuilder<DownloadWorker>()
                .setInputData(input)
                .addTag(TAG_DOWNLOAD)
                .build()
        workManager.enqueue(work)
    }

    private companion object {
        const val TAG_DOWNLOAD = "release_download"
    }
}
