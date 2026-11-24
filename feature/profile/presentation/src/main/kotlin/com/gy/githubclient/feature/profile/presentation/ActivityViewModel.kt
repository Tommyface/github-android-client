/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.profile.domain.model.UserEvent
import com.gy.githubclient.feature.profile.domain.usecase.ListUserEventsUseCase
import com.gy.githubclient.feature.profile.domain.usecase.ObserveProfileUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

/** 个人动态页 UI 状态。 */
data class ActivityUiState(
    val events: List<UserEvent> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
)

/** 个人动态 ViewModel（v0.10.0 N4）。 */
class ActivityViewModel(
    private val observeProfile: ObserveProfileUseCase,
    private val listUserEvents: ListUserEventsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ActivityUiState())
    val uiState: StateFlow<ActivityUiState> = _uiState.asStateFlow()

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
            when (val result = listUserEvents(profile.login)) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(events = result.data, isLoading = false)
                is NetworkResult.Failure ->
                    _uiState.value =
                        _uiState.value.copy(isLoading = false, message = errorMessage(result.error))
            }
        }
    }

    private fun errorMessage(error: ApiError): String =
        when (error) {
            ApiError.Unauthorized -> "登录已失效，请重新登录"
            is ApiError.RateLimited -> "API 限流，请稍后再试"
            ApiError.NotFound -> "未找到用户动态"
            is ApiError.Network -> "网络错误：${error.cause}"
            is ApiError.Http -> "请求失败（${error.code}）"
            is ApiError.Unknown -> "未知错误：${error.message}"
        }
}
