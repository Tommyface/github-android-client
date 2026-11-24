/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.model.mirror.MirrorConfig
import com.gy.githubclient.core.domain.model.mirror.MirrorPreference
import com.gy.githubclient.feature.download.domain.usecase.MeasureMirrorLatenciesUseCase
import com.gy.githubclient.feature.download.domain.usecase.MirrorSettingsView
import com.gy.githubclient.feature.download.domain.usecase.ObserveMirrorSettingsUseCase
import com.gy.githubclient.feature.download.domain.usecase.SetMirrorPreferenceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 镜像设置页 UI 状态。 */
data class MirrorSettingsUiState(
    val view: MirrorSettingsView = MirrorSettingsView(emptyList(), MirrorPreference.Direct, emptyMap()),
    val isMeasuring: Boolean = false,
    val message: String? = null,
)

/** 镜像设置 ViewModel：测速、选择偏好、自定义模板。 */
class MirrorSettingsViewModel(
    observeSettings: ObserveMirrorSettingsUseCase,
    private val measure: MeasureMirrorLatenciesUseCase,
    private val setPreference: SetMirrorPreferenceUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MirrorSettingsUiState())
    val uiState: StateFlow<MirrorSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            observeSettings().collect { view ->
                _uiState.value = _uiState.value.copy(view = view)
            }
        }
    }

    fun onMeasure() {
        if (_uiState.value.isMeasuring) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isMeasuring = true)
            val result = measure()
            val message =
                if (result.isSuccess) {
                    "测速完成，共 ${result.getOrNull()?.size ?: 0} 个镜像可用"
                } else {
                    "测速失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
                }
            _uiState.value = _uiState.value.copy(message = message, isMeasuring = false)
        }
    }

    fun onSelectDirect() = viewModelScope.launch { setPreference(MirrorPreference.Direct) }

    fun onSelectMirror(id: String) = viewModelScope.launch { setPreference(MirrorPreference.Selected(id)) }

    fun onSetCustom(template: String) = viewModelScope.launch { setPreference(MirrorPreference.Custom(template)) }
}

/**
 * 按测速延迟升序排列镜像（v0.6.0 M9，纯函数可单测）：
 * 已测速者在前（快→慢），未测速/测速失败者保持原相对顺序排后。
 */
internal fun sortMirrorsByLatency(
    mirrors: List<MirrorConfig>,
    latencies: Map<String, Int>,
): List<MirrorConfig> =
    mirrors
        .withIndex()
        .sortedWith(
            compareBy(
                { (_, m) -> latencies[m.id] ?: Int.MAX_VALUE },
                { (i, _) -> i },
            ),
        ).map { it.value }
