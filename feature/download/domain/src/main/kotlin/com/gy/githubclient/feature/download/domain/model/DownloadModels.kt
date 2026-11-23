/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.domain.model

/** 下载任务状态机。 */
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    PAUSED,
    DONE,
    FAILED,
    CANCELLED,
    ;

    val isTerminal: Boolean
        get() = this == DONE || this == FAILED || this == CANCELLED

    companion object {
        fun fromWire(value: String): DownloadStatus = entries.firstOrNull { it.name == value.uppercase() } ?: QUEUED
    }
}

/** 下载请求：由调用方（Release 详情页）提交。 */
@Suppress("LongParameterList")
data class DownloadRequest(
    val url: String,
    val fileName: String,
    val suggestedFileName: String? = null,
    val repoOwner: String? = null,
    val repoName: String? = null,
    val releaseTag: String? = null,
    val assetId: Long = 0L,
    val totalBytes: Long = -1L,
    val useMirror: Boolean = true,
)

/** 下载任务（持久化 + UI 展示）。 */
@Suppress("LongParameterList")
data class DownloadTask(
    val id: String,
    val request: DownloadRequest,
    val status: DownloadStatus,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long? = null,
    val percent: Int? = null,
    val filePath: String? = null,
    val error: String? = null,
    val createdAt: String,
    val updatedAt: String,
    val mirrorName: String? = null,
) {
    /** 进度百分比（0-100），未知总数返回 null。 */
    val safePercent: Int?
        get() =
            percent ?: totalBytes?.takeIf { it > 0 }?.let { total ->
                ((bytesDownloaded * 100) / total).toInt().coerceIn(0, 100)
            }
}

/** 下载统计（管理页顶部汇总）。 */
data class DownloadStats(
    val active: Int,
    val done: Int,
    val failed: Int,
    val totalBytesDownloaded: Long,
)
