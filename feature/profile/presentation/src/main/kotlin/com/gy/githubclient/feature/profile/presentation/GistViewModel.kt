/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.profile.domain.model.GistItem
import com.gy.githubclient.feature.profile.domain.usecase.GetGistUseCase
import com.gy.githubclient.feature.profile.domain.usecase.ListUserGistsUseCase
import com.gy.githubclient.feature.profile.domain.usecase.ObserveProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/** Gist 列表页 UI 状态（v0.15.0 A6）。 */
data class GistUiState(
    val gists: List<GistItem> = emptyList(),
    val detail: GistItem? = null,
    val isLoading: Boolean = false,
    val message: String? = null,
)

/** Gist 浏览 ViewModel（只读，v0.15.0 A6）。 */
class GistViewModel(
    private val observeProfile: ObserveProfileUseCase,
    private val listUserGists: ListUserGistsUseCase,
    private val getGist: GetGistUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(GistUiState())
    val uiState: StateFlow<GistUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            val profile = observeProfile().firstOrNull()
            if (profile == null) {
                _uiState.value = _uiState.value.copy(message = "未获取到用户信息，请先登录", isLoading = false)
                return@launch
            }
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            when (val result = listUserGists(profile.login)) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(gists = result.data, isLoading = false)
                is NetworkResult.Failure ->
                    _uiState.value = _uiState.value.copy(isLoading = false, message = errorMessage(result.error))
            }
        }
    }

    /** 打开单条 Gist 详情（含文件正文）。 */
    fun openDetail(id: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null)
            when (val result = getGist(id)) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(detail = result.data, isLoading = false)
                is NetworkResult.Failure ->
                    _uiState.value = _uiState.value.copy(isLoading = false, message = errorMessage(result.error))
            }
        }
    }

    fun closeDetail() {
        _uiState.value = _uiState.value.copy(detail = null)
    }

    private fun errorMessage(error: ApiError): String =
        when (error) {
            ApiError.Unauthorized -> "登录已失效，请重新登录"
            is ApiError.RateLimited -> "API 限流，请稍后再试"
            ApiError.NotFound -> "未找到 Gist"
            is ApiError.Network -> "网络错误：${error.cause}"
            is ApiError.Http -> "请求失败（${error.code}）"
            is ApiError.Unknown -> "未知错误：${error.message}"
        }
}
