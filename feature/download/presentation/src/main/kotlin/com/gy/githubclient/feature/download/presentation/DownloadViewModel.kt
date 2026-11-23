/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.feature.download.domain.model.DownloadStats
import com.gy.githubclient.feature.download.domain.model.DownloadTask
import com.gy.githubclient.feature.download.domain.usecase.CancelDownloadUseCase
import com.gy.githubclient.feature.download.domain.usecase.ClearCompletedDownloadsUseCase
import com.gy.githubclient.feature.download.domain.usecase.ObserveDownloadStatsUseCase
import com.gy.githubclient.feature.download.domain.usecase.ObserveDownloadsUseCase
import com.gy.githubclient.feature.download.domain.usecase.RemoveDownloadUseCase
import com.gy.githubclient.feature.download.domain.usecase.RetryDownloadUseCase
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 下载管理页 ViewModel。 */
class DownloadViewModel(
    observeDownloads: ObserveDownloadsUseCase,
    observeStats: ObserveDownloadStatsUseCase,
    private val cancel: CancelDownloadUseCase,
    private val retry: RetryDownloadUseCase,
    private val remove: RemoveDownloadUseCase,
    private val clearCompleted: ClearCompletedDownloadsUseCase,
) : ViewModel() {
    val tasks: StateFlow<List<DownloadTask>> =
        observeDownloads().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val stats: StateFlow<DownloadStats> =
        observeStats().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DownloadStats(0, 0, 0, 0L))

    fun onCancel(id: String) = viewModelScope.launch { cancel(id) }

    fun onRetry(id: String) = viewModelScope.launch { retry(id) }

    fun onRemove(id: String) = viewModelScope.launch { remove(id) }

    fun onClearCompleted() = viewModelScope.launch { clearCompleted() }
}
