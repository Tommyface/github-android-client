/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.network

import com.gy.githubclient.core.domain.model.installation.DownloadProgress
import kotlinx.coroutines.flow.Flow

data class AssetIdentity(
    val assetId: Long = 0L,
    val digest: String? = null,
    val size: Long = -1L,
)

interface Downloader {
    fun download(
        url: String,
        suggestedFileName: String? = null,
        bypassMirror: Boolean = false,
        identity: AssetIdentity? = null,
    ): Flow<DownloadProgress>

    suspend fun saveToFile(
        url: String,
        suggestedFileName: String? = null,
    ): String

    suspend fun getDownloadedFilePath(fileName: String): String?

    suspend fun cancelDownload(fileName: String): Boolean

    suspend fun discardPartial(fileName: String): Boolean

    suspend fun reclaimOrphanedPartials(claimedNames: Set<String>): Int
}
