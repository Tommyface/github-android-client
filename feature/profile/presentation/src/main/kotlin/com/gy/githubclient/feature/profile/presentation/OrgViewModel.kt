/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.profile.domain.model.OrgMember
import com.gy.githubclient.feature.profile.domain.model.OrgSummary
import com.gy.githubclient.feature.profile.domain.usecase.GetOrgUseCase
import com.gy.githubclient.feature.profile.domain.usecase.ListOrgMembersUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 组织信息页 UI 状态（v0.15.0 A5）。 */
data class OrgUiState(
    val query: String = "",
    val org: OrgSummary? = null,
    val members: List<OrgMember> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
)

/** 组织信息 ViewModel（只读，v0.15.0 A5）。 */
class OrgViewModel(
    private val getOrg: GetOrgUseCase,
    private val listOrgMembers: ListOrgMembersUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(OrgUiState())
    val uiState: StateFlow<OrgUiState> = _uiState.asStateFlow()

    fun onQueryChange(value: String) {
        _uiState.value = _uiState.value.copy(query = value)
    }

    fun load() {
        val login = _uiState.value.query.trim()
        if (login.isBlank()) {
            _uiState.value = _uiState.value.copy(message = "请输入组织名称")
            return
        }
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, message = null, org = null, members = emptyList())
            when (val result = getOrg(login)) {
                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(org = result.data, isLoading = false)
                    loadMembers(login)
                }
                is NetworkResult.Failure ->
                    _uiState.value = _uiState.value.copy(isLoading = false, message = errorMessage(result.error))
            }
        }
    }

    private suspend fun loadMembers(login: String) {
        when (val result = listOrgMembers(login)) {
            is NetworkResult.Success -> _uiState.value = _uiState.value.copy(members = result.data)
            // 成员列表失败不清除已加载的组织概要：public members 可能因隐私隐藏，静默降级。
            is NetworkResult.Failure -> Unit
        }
    }

    private fun errorMessage(error: ApiError): String =
        when (error) {
            ApiError.Unauthorized -> "登录已失效，请重新登录"
            is ApiError.RateLimited -> "API 限流，请稍后再试"
            ApiError.NotFound -> "未找到该组织"
            is ApiError.Network -> "网络错误：${error.cause}"
            is ApiError.Http -> "请求失败（${error.code}）"
            is ApiError.Unknown -> "未知错误：${error.message}"
        }
}
