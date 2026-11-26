/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.update

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.BuildConfig
import com.gy.githubclient.core.domain.update.UpdateCandidate
import com.gy.githubclient.core.domain.update.UpdateCoordinator
import com.gy.githubclient.core.domain.update.UpdateLevel
import com.gy.githubclient.core.domain.update.UpdateOutcome
import com.gy.githubclient.feature.download.domain.model.DownloadRequest
import com.gy.githubclient.feature.download.domain.repository.DownloadManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 检查更新页 UI 状态。
 *
 * 与 P-101 的判定级别一一对应（NONE → 完全静默；UNKNOWN → 手动检查时给一行说明，不打扰）：
 * - [Idle] / [Checking]：未开始 / 检查中
 * - [UpToDate]：已是最新（静默，不弹窗）
 * - [Available]：有更新（[forced] 为 true 时不可跳过）
 * - [Skipped]：用户跳过过的指纹（静默）
 * - [Silent]：无法比较 / 无远端信息（静默）
 * - [Queued]：已入队下载
 * - [Error]：可行动的错误
 */
sealed class UpdateCheckUiState {
    data object Idle : UpdateCheckUiState()

    data object Checking : UpdateCheckUiState()

    data class UpToDate(
        val currentVersion: String,
        val reason: String = "",
    ) : UpdateCheckUiState()

    data class Available(
        val info: UpdateChecker.UpdateInfo,
        val forced: Boolean,
        val reason: String = "",
    ) : UpdateCheckUiState()

    data class Skipped(
        val version: String,
    ) : UpdateCheckUiState()

    data class Silent(
        val reason: String,
    ) : UpdateCheckUiState()

    data class Queued(
        val taskId: String,
        val fileName: String,
    ) : UpdateCheckUiState()

    data class Error(
        val message: String,
    ) : UpdateCheckUiState()
}

/**
 * 手动检查更新 ViewModel（M1-02~M1-04）。
 *
 * 判定链路：**双通道候选** → [UpdateCoordinator] → [UpdateLevelDecider] → UI 状态。
 * 与旧实现的关键差异：
 *  - 判定依据从"单一 tag 比较"升级为「版本号 + versionCode + 产物指纹」三信号；
 *  - 支持**跳过记指纹**（同版本重打包不会被反复提醒）；
 *  - 支持**安装 TTL**（点过安装后 30 分钟内不再重复提醒，防装失败死循环）；
 *  - 已是最新 / 无法比较 / 已跳过 → 一律静默，不打扰用户。
 */
class UpdateCheckViewModel(
    private val appContext: Context,
    private val downloadManager: DownloadManager,
    private val coordinator: UpdateCoordinator,
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

    /** 手动检查（前台触发，不受后台节流限制）。 */
    fun checkNow() {
        if (_uiState.value is UpdateCheckUiState.Checking) return
        viewModelScope.launch {
            _uiState.value = UpdateCheckUiState.Checking
            val outcome =
                withContext(Dispatchers.IO) {
                    runCatching {
                        coordinator.evaluate(
                            localVersion = BuildConfig.VERSION_NAME,
                            localVersionCode = BuildConfig.VERSION_CODE,
                        )
                    }
                }
            coordinator.recordCheck()
            _uiState.value =
                outcome.fold(
                    onSuccess = ::toUiState,
                    onFailure = { throwable ->
                        UpdateCheckUiState.Error(throwable.message ?: "检查失败，请稍后重试")
                    },
                )
        }
    }

    /** 把领域结论映射为 UI 状态（纯映射，便于单测）。 */
    internal fun toUiState(outcome: UpdateOutcome): UpdateCheckUiState {
        // 先取到局部变量：跨模块的 data class 属性不能智能转换。
        val candidate = outcome.candidate
        return when (outcome.level) {
            UpdateLevel.NONE ->
                if (candidate == null) {
                    UpdateCheckUiState.UpToDate(outcome.currentVersion, outcome.reason)
                } else {
                    UpdateCheckUiState.Skipped(candidate.version)
                }

            UpdateLevel.OPTIONAL ->
                candidate?.let {
                    UpdateCheckUiState.Available(it.toUpdateInfo(), forced = false, reason = outcome.reason)
                } ?: UpdateCheckUiState.UpToDate(outcome.currentVersion, outcome.reason)

            UpdateLevel.FORCED ->
                candidate?.let {
                    UpdateCheckUiState.Available(it.toUpdateInfo(), forced = true, reason = outcome.reason)
                } ?: UpdateCheckUiState.UpToDate(outcome.currentVersion, outcome.reason)

            UpdateLevel.UNKNOWN -> UpdateCheckUiState.Silent(outcome.reason)
        }
    }

    /** 「跳过此版」：按**指纹**记录（不是版本号），同指纹不再提醒。 */
    fun skipCurrentVersion() {
        val candidate = (uiState.value as? UpdateCheckUiState.Available)?.info ?: return
        coordinator.skip(
            UpdateCandidate(
                version = candidate.tagName.removePrefix("v"),
                fingerprint = candidate.sha256,
            ),
        )
        _uiState.value = UpdateCheckUiState.Skipped(candidate.tagName)
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
                .onSuccess { taskId ->
                    // 记录安装点击：进入安装 TTL，避免安装失败时反复提醒。
                    coordinator.markInstallRequested()
                    _uiState.value = UpdateCheckUiState.Queued(taskId, fileName)
                }
                .onFailure { throwable ->
                    _uiState.value = UpdateCheckUiState.Error(throwable.message ?: "入队失败，请稍后重试")
                }
        }
    }
}

/** 候选 → 旧版下载流程用的 [UpdateChecker.UpdateInfo]（保持下载链路不变）。 */
internal fun UpdateCandidate.toUpdateInfo(): UpdateChecker.UpdateInfo =
    UpdateChecker.UpdateInfo(
        tagName = version,
        downloadUrl = downloadUrl,
        htmlUrl = downloadUrl.orEmpty(),
        sha256 = fingerprint,
        assetSize = assetSize,
    )
