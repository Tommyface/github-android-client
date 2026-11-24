/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.library.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.feature.download.domain.model.DownloadRequest
import com.gy.githubclient.feature.download.domain.usecase.EnqueueDownloadUseCase
import com.gy.githubclient.feature.library.domain.model.DownloadCandidate
import com.gy.githubclient.feature.library.domain.model.LibraryEntry
import com.gy.githubclient.feature.library.domain.model.TrackedApp
import com.gy.githubclient.feature.library.domain.usecase.AddTrackedUseCase
import com.gy.githubclient.feature.library.domain.usecase.CheckUpdatesUseCase
import com.gy.githubclient.feature.library.domain.usecase.ObserveTrackedUseCase
import com.gy.githubclient.feature.library.domain.usecase.RemoveTrackedUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 应用库 UI 状态。 */
data class LibraryUiState(
    val entries: List<LibraryEntry> = emptyList(),
    val isChecking: Boolean = false,
)

/** 一次性事件。 */
sealed interface LibraryEvent {
    data class ShowMessage(
        val message: String,
    ) : LibraryEvent
}

/**
 * 应用库 ViewModel：追踪列表来自 Room，检查结果在内存中，一键/单项更新入下载引擎。
 * 下载请求由本模块自行构造（url 用官方直链、expectedSha256 透传），
 * 下载引擎内部的镜像解析与 SHA-256 + 签名双闸校验照常生效。
 */
@Suppress("LongParameterList")
class LibraryViewModel(
    private val observeTracked: ObserveTrackedUseCase,
    private val addTracked: AddTrackedUseCase,
    private val removeTracked: RemoveTrackedUseCase,
    private val checkUpdates: CheckUpdatesUseCase,
    private val enqueueDownload: EnqueueDownloadUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var cachedTracked: List<TrackedApp> = emptyList()

    init {
        viewModelScope.launch {
            observeTracked().collect { apps ->
                cachedTracked = apps
                _uiState.value = _uiState.value.copy(entries = apps.map { LibraryEntry(app = it) })
            }
        }
    }

    fun add(
        owner: String,
        name: String,
        displayName: String,
        installedVersion: String,
        abi: String,
    ) {
        val trimmedOwner = owner.trim()
        val trimmedName = name.trim()
        if (trimmedOwner.isBlank() || trimmedName.isBlank()) return
        viewModelScope.launch {
            addTracked(
                TrackedApp(
                    owner = trimmedOwner,
                    name = trimmedName,
                    displayName = displayName.ifBlank { trimmedName },
                    installedVersion = installedVersion.trim(),
                    abi = abi,
                    addedAt = System.currentTimeMillis().toString(),
                ),
            )
        }
    }

    fun remove(fullName: String) {
        viewModelScope.launch { removeTracked(fullName) }
    }

    fun checkAll() {
        if (cachedTracked.isEmpty() || _uiState.value.isChecking) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isChecking = true)
            val results = checkUpdates(cachedTracked)
            _uiState.value = _uiState.value.copy(entries = results, isChecking = false)
        }
    }

    /** 更新单个应用：把候选资产构造为下载请求并入队。 */
    fun update(entry: LibraryEntry) {
        val candidate = entry.download ?: return
        viewModelScope.launch {
            enqueueDownload(buildRequest(candidate, entry.app.owner, entry.app.name, entry.latestVersion.orEmpty()))
        }
    }

    /** 一键更新所有有更新的应用。 */
    fun updateAll() {
        _uiState.value.entries
            .filter { it.hasUpdate && it.download != null }
            .forEach(::update)
    }

    internal fun buildRequest(
        candidate: DownloadCandidate,
        owner: String,
        name: String,
        tag: String,
    ): DownloadRequest =
        DownloadRequest(
            url = candidate.url,
            fileName = candidate.assetName.ifBlank { "download.apk" },
            suggestedFileName = candidate.assetName.ifBlank { null },
            repoOwner = owner,
            repoName = name,
            releaseTag = tag,
            totalBytes = candidate.size,
            useMirror = true,
            expectedSha256 = candidate.sha256,
        )
}
