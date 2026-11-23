/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.domain

import com.gy.githubclient.feature.download.domain.model.DownloadRequest
import com.gy.githubclient.feature.download.domain.model.DownloadStats
import com.gy.githubclient.feature.download.domain.model.DownloadStatus
import com.gy.githubclient.feature.download.domain.model.DownloadTask
import com.gy.githubclient.feature.download.domain.repository.DownloadManager
import com.gy.githubclient.feature.download.domain.usecase.CancelDownloadUseCase
import com.gy.githubclient.feature.download.domain.usecase.EnqueueDownloadUseCase
import com.gy.githubclient.feature.download.domain.usecase.ObserveDownloadStatsUseCase
import com.gy.githubclient.feature.download.domain.usecase.ObserveDownloadsUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 下载领域层：状态机、进度计算、用例委托。 */
class DownloadDomainTest {
    private val request =
        DownloadRequest(
            url = "https://github.com/o/r/releases/download/v1/a.apk",
            fileName = "a.apk",
            assetId = 7L,
            totalBytes = 1000L,
        )

    private val manager =
        object : DownloadManager {
            var lastEnqueued: DownloadRequest? = null
            var cancelled: String? = null

            override fun observeTasks(): Flow<List<DownloadTask>> = flowOf(emptyList())

            override fun observeStats(): Flow<DownloadStats> = flowOf(DownloadStats(0, 0, 0, 0L))

            override suspend fun getTask(id: String): DownloadTask? = null

            override suspend fun enqueue(req: DownloadRequest): String {
                lastEnqueued = req
                return "task-1"
            }

            override suspend fun cancel(id: String) {
                cancelled = id
            }

            override suspend fun retry(id: String) = Unit

            override suspend fun remove(id: String) = Unit

            override suspend fun clearCompleted() = Unit
        }

    @Test
    fun statusTerminalAndWireParsing() {
        assertTrue(DownloadStatus.DONE.isTerminal)
        assertTrue(DownloadStatus.CANCELLED.isTerminal)
        assertFalse(DownloadStatus.DOWNLOADING.isTerminal)
        assertEquals(DownloadStatus.FAILED, DownloadStatus.fromWire("failed"))
        assertEquals(DownloadStatus.QUEUED, DownloadStatus.fromWire("???"))
    }

    @Test
    fun safePercentComputesFromBytesWhenPercentNull() {
        val task =
            DownloadTask(
                id = "t1",
                request = request,
                status = DownloadStatus.DOWNLOADING,
                bytesDownloaded = 250L,
                totalBytes = 1000L,
                percent = null,
                createdAt = "now",
                updatedAt = "now",
            )
        assertEquals(25, task.safePercent)

        val unknown = task.copy(totalBytes = null)
        assertEquals(null, unknown.safePercent)
    }

    @Test
    fun observeUseCasesDelegate() =
        runTest {
            assertEquals(emptyList<DownloadTask>(), ObserveDownloadsUseCase(manager)().first())
            assertEquals(DownloadStats(0, 0, 0, 0L), ObserveDownloadStatsUseCase(manager)().first())
        }

    @Test
    fun enqueueAndCancelDelegate() =
        runTest {
            val id = EnqueueDownloadUseCase(manager)(request)
            assertEquals("task-1", id)
            assertEquals(request, manager.lastEnqueued)

            CancelDownloadUseCase(manager)("task-1")
            assertEquals("task-1", manager.cancelled)
        }
}
