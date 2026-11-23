/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gy.githubclient.feature.download.domain.model.DownloadRequest
import com.gy.githubclient.feature.download.domain.model.DownloadStatus
import com.gy.githubclient.feature.download.domain.model.DownloadTask

@Suppress("LongParameterList")
@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val url: String,
    val fileName: String,
    val suggestedFileName: String?,
    val repoOwner: String?,
    val repoName: String?,
    val releaseTag: String?,
    val assetId: Long,
    val totalBytes: Long,
    val useMirror: Boolean,
    val status: String,
    val bytesDownloaded: Long,
    val totalBytesActual: Long?,
    val percent: Int?,
    val filePath: String?,
    val error: String?,
    val createdAt: String,
    val updatedAt: String,
    val mirrorName: String?,
)

fun DownloadTask.toEntity(): DownloadTaskEntity =
    DownloadTaskEntity(
        id = id,
        url = request.url,
        fileName = request.fileName,
        suggestedFileName = request.suggestedFileName,
        repoOwner = request.repoOwner,
        repoName = request.repoName,
        releaseTag = request.releaseTag,
        assetId = request.assetId,
        totalBytes = request.totalBytes,
        useMirror = request.useMirror,
        status = status.name,
        bytesDownloaded = bytesDownloaded,
        totalBytesActual = totalBytes,
        percent = percent,
        filePath = filePath,
        error = error,
        createdAt = createdAt,
        updatedAt = updatedAt,
        mirrorName = mirrorName,
    )

fun DownloadTaskEntity.toDomain(): DownloadTask =
    DownloadTask(
        id = id,
        request =
            DownloadRequest(
                url = url,
                fileName = fileName,
                suggestedFileName = suggestedFileName,
                repoOwner = repoOwner,
                repoName = repoName,
                releaseTag = releaseTag,
                assetId = assetId,
                totalBytes = totalBytes,
                useMirror = useMirror,
            ),
        status = DownloadStatus.fromWire(status),
        bytesDownloaded = bytesDownloaded,
        totalBytes = totalBytesActual,
        percent = percent,
        filePath = filePath,
        error = error,
        createdAt = createdAt,
        updatedAt = updatedAt,
        mirrorName = mirrorName,
    )
