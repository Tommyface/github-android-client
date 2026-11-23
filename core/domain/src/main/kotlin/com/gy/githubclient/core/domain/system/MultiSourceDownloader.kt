/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.system

import com.gy.githubclient.core.domain.model.installation.DownloadProgress
import com.gy.githubclient.core.domain.network.AssetIdentity
import kotlinx.coroutines.flow.Flow

interface MultiSourceDownloader {
    fun download(
        githubUrl: String,
        suggestedFileName: String? = null,
        identity: AssetIdentity? = null,
    ): Flow<DownloadProgress>
}
