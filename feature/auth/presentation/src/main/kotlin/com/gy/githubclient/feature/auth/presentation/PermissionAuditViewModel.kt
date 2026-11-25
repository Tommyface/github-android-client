/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.audit.AuditLogRepository
import com.gy.githubclient.core.audit.AuditRecord
import com.gy.githubclient.feature.auth.domain.model.PermissionReport
import com.gy.githubclient.feature.auth.domain.usecase.AuditPermissionsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 权限体检页 UI 状态。 */
data class PermissionAuditUiState(
    val isLoading: Boolean = false,
    val report: PermissionReport? = null,
    val errorMessage: String? = null,
)

/** 权限体检：展示当前凭证「能做什么 / 缺什么」。 */
class PermissionAuditViewModel(
    private val auditPermissions: AuditPermissionsUseCase,
    private val auditLogRepository: AuditLogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PermissionAuditUiState())
    val uiState = _uiState.asStateFlow()

    /** 最近写操作审计（受 Room 上限 500 条约束）。 */
    val auditEntries =
        auditLogRepository.observeRecent().stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList<AuditRecord>(),
        )

    fun clearAudit() {
        viewModelScope.launch { auditLogRepository.clear() }
    }

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = PermissionAuditUiState(isLoading = true)
            _uiState.value =
                @Suppress("TooGenericExceptionCaught")
                try {
                    PermissionAuditUiState(report = auditPermissions())
                } catch (t: Exception) {
                    PermissionAuditUiState(errorMessage = t.message ?: "体检失败，请重试")
                }
        }
    }
}
