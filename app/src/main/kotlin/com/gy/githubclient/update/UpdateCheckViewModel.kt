/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.feature.download.domain.model.DownloadRequest
import com.gy.githubclient.feature.download.domain.repository.DownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 检查更新页 UI 状态。 */
sealed class UpdateCheckUiState {
    data object Idle : UpdateCheckUiState()

    data object Checking : UpdateCheckUiState()

    /** 已是最新版本。 */
    data class UpToDate(
        val currentVersion: String,
    ) : UpdateCheckUiState()

    /** 发现新版本：携带完整 UpdateInfo 供下载入队。 */
    data class Available(
        val info: UpdateChecker.UpdateInfo,
    ) : UpdateCheckUiState()

    /** 已入队应用内下载（下载进度看下载管理页）。 */
    data class Queued(
        val taskId: String,
        val fileName: String,
    ) : UpdateCheckUiState()

    data class Error(
        val message: String,
    ) : UpdateCheckUiState()
}

/**
 * 手动检查更新 ViewModel（v0.6.0 M5/M8）：
 * - 手动触发 UpdateChecker，展示版本/大小/官方 SHA-256 前 16 位；
 * - 「应用内下载」直接入队下载引擎并透传 expectedSha256，
 *   下载完成由 SHA-256 校验 + APK 签名指纹校验双闸把关（不再跳浏览器裸下）。
 * - 预发布开关持久于 [UpdateCheckPrefs]，与后台 Worker 共用。
 */
class UpdateCheckViewModel(
    private val appContext: Context,
    private val downloadManager: DownloadManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow<UpdateCheckUiState>(UpdateCheckUiState.Idle)
    val uiState: StateFlow<UpdateCheckUiState> = _uiState.asStateFlow()

    private val prefs = appContext.getSharedPreferences(UpdateCheckPrefs.NAME, Context.MODE_PRIVATE)

    val includePrerelease: Boolean
        get() = prefs.getBoolean(UpdateCheckPrefs.KEY_INCLUDE_PRERELEASE, false)

    fun setIncludePrerelease(enabled: Boolean) {
        prefs.edit().putBoolean(UpdateCheckPrefs.KEY_INCLUDE_PRERELEASE, enabled).apply()
        _uiState.value = UpdateCheckUiState.Idle // 开关切换后旧结果失效
    }

    fun checkNow() {
        if (_uiState.value is UpdateCheckUiState.Checking) return
        viewModelScope.launch {
            _uiState.value = UpdateCheckUiState.Checking
            val result =
                withContext(Dispatchers.IO) {
                    runCatching { UpdateChecker(appContext).check(includePrerelease) }
                }
            _uiState.value =
                result.fold(
                    onSuccess = { info ->
                        if (info == null) {
                            UpdateCheckUiState.UpToDate(com.gy.githubclient.BuildConfig.VERSION_NAME)
                        } else {
                            UpdateCheckUiState.Available(info)
                        }
                    },
                    onFailure = { throwable ->
                        UpdateCheckUiState.Error(throwable.message ?: "检查失败，请稍后重试")
                    },
                )
        }
    }

    /** 应用内下载：摘要透传下载引擎，落盘后自动做 SHA-256 + 签名指纹双校验。 */
    fun download(info: UpdateChecker.UpdateInfo) {
        val url =
            info.downloadUrl ?: run {
                _uiState.value = UpdateCheckUiState.Error("该 Release 未附 APK 资产，请到 Release 页手动下载")
                return
            }
        viewModelScope.launch {
            val fileName =
                "github-android-client-${info.tagName.removePrefix("v")}.apk"
            val request =
                DownloadRequest(
                    url = url,
                    fileName = fileName,
                    suggestedFileName = fileName,
                    repoOwner = "Tommyface",
                    repoName = "github-android-client",
                    releaseTag = info.tagName,
                    totalBytes = info.assetSize,
                    useMirror = true,
                    expectedSha256 = info.sha256,
                )
            runCatching { downloadManager.enqueue(request) }
                .onSuccess { taskId -> _uiState.value = UpdateCheckUiState.Queued(taskId, fileName) }
                .onFailure { throwable ->
                    _uiState.value = UpdateCheckUiState.Error(throwable.message ?: "入队失败，请稍后重试")
                }
        }
    }
}
