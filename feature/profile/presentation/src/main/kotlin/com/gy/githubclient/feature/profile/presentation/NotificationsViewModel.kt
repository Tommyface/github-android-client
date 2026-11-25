/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.core.mutation.MutationResult
import com.gy.githubclient.core.mutation.userMessage
import com.gy.githubclient.feature.profile.domain.model.GitHubNotification
import com.gy.githubclient.feature.profile.domain.usecase.ListNotificationsUseCase
import com.gy.githubclient.feature.profile.domain.usecase.MarkAllNotificationsReadUseCase
import com.gy.githubclient.feature.profile.domain.usecase.MarkNotificationReadUseCase
import com.gy.githubclient.feature.profile.domain.usecase.SetThreadSubscriptionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 通知页 UI 状态。 */
data class NotificationsUiState(
    val isLoading: Boolean = false,
    val items: List<GitHubNotification> = emptyList(),
    /** 已本地标记已读的线程 id（乐观更新）。 */
    val locallyReadIds: Set<Long> = emptySet(),
    /** 已忽略（mute）的线程 id；本地乐观状态，失败回滚。 */
    val ignoredIds: Set<Long> = emptySet(),
    /** 批量选择模式（githup 通知批量处理）。 */
    val selectionMode: Boolean = false,
    val selectedIds: Set<Long> = emptySet(),
    val errorMessage: String? = null,
) {
    /** 展示中的未读数：本地标记已读的条目立即从未读计数里移除。 */
    val unreadCount: Int get() = items.count { it.unread && it.id !in locallyReadIds }
}

/**
 * 通知中心 ViewModel（v0.6.0 M7，通知能力对齐 githup）：
 * - 进入页面拉取未读通知；点击条目乐观标记已读并跳转主题网页；
 * - 支持「全部标为已读」与多选批量已读；
 * - subject.url 是 REST 地址，统一由 [GitHubNotification.subjectWebUrl] 转网页地址。
 */
class NotificationsViewModel(
    private val listNotifications: ListNotificationsUseCase,
    private val markRead: MarkNotificationReadUseCase,
    private val markAllReadUseCase: MarkAllNotificationsReadUseCase,
    private val setThreadSubscription: SetThreadSubscriptionUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            _uiState.value =
                when (val result = listNotifications(all = false)) {
                    is NetworkResult.Success ->
                        _uiState.value.copy(
                            isLoading = false,
                            items = result.data,
                            selectedIds = _uiState.value.selectedIds intersect result.data.map { it.id }.toSet(),
                        )

                    is NetworkResult.Failure ->
                        _uiState.value.copy(
                            isLoading = false,
                            errorMessage = errorMessageFor(result),
                        )
                }
        }
    }

    /**
     * 点击一条未读通知：立即本地标记已读，同时后台 PATCH 线程端点。
     * 返回该通知的主题网页 URL（无则 null）。
     */
    fun onOpen(notification: GitHubNotification): String? {
        if (notification.unread && notification.id !in _uiState.value.locallyReadIds) {
            _uiState.value = _uiState.value.copy(locallyReadIds = _uiState.value.locallyReadIds + notification.id)
            viewModelScope.launch { markRead(notification.threadId) }
        }
        return notification.subjectWebUrl
    }

    fun toggleSelectionMode() {
        val state = _uiState.value
        _uiState.value =
            state.copy(
                selectionMode = !state.selectionMode,
                selectedIds = if (state.selectionMode) emptySet() else state.selectedIds,
            )
    }

    fun toggleSelected(id: Long) {
        val state = _uiState.value
        if (!state.selectionMode) return
        _uiState.value =
            state.copy(
                selectedIds =
                    if (id in state.selectedIds) {
                        state.selectedIds - id
                    } else {
                        state.selectedIds + id
                    },
            )
    }

    /** 把当前选中的线程批量标记已读（逐条 PATCH，失败保留可重试状态）。 */
    fun markSelectedRead() {
        val state = _uiState.value
        if (state.selectedIds.isEmpty()) return
        val ids = state.selectedIds.toList()
        _uiState.value =
            state.copy(
                locallyReadIds = state.locallyReadIds + ids,
                selectedIds = emptySet(),
                selectionMode = false,
                errorMessage = null,
            )
        viewModelScope.launch {
            var failed = 0
            ids.forEach { id ->
                if (markRead(id) is NetworkResult.Failure) failed++
            }
            if (failed > 0) {
                _uiState.value =
                    _uiState.value.copy(errorMessage = "有 $failed 条通知标记失败，请重试")
            }
        }
    }

    /** 一键全部标为已读：PUT /notifications。 */
    fun markAllRead() {
        val state = _uiState.value
        val allIds = state.items.map { it.id }.toSet()
        if (allIds.isEmpty()) return
        _uiState.value =
            state.copy(
                locallyReadIds = state.locallyReadIds + allIds,
                selectedIds = emptySet(),
                selectionMode = false,
                errorMessage = null,
            )
        viewModelScope.launch {
            if (markAllReadUseCase() is NetworkResult.Failure) {
                _uiState.value = _uiState.value.copy(errorMessage = "全部标为已读失败，请稍后重试")
            }
        }
    }

    /** 忽略 / 恢复线程通知：先改 UI，失败回滚并提示。 */
    fun toggleIgnore(notification: GitHubNotification) {
        val state = _uiState.value
        val ignored = notification.id !in state.ignoredIds
        val nextIgnored =
            if (ignored) {
                state.ignoredIds + notification.id
            } else {
                state.ignoredIds - notification.id
            }
        _uiState.value = state.copy(ignoredIds = nextIgnored, errorMessage = null)
        viewModelScope.launch {
            when (val result = setThreadSubscription(notification.threadId, ignored)) {
                is MutationResult.Success ->
                    _uiState.value =
                        _uiState.value.copy(
                            errorMessage = if (ignored) "已忽略该线程的通知" else "已恢复该线程的通知",
                        )

                is MutationResult.Failure -> {
                    _uiState.value = _uiState.value.copy(ignoredIds = state.ignoredIds, errorMessage = result.error.userMessage())
                }
            }
        }
    }

    private fun errorMessageFor(failure: NetworkResult.Failure): String =
        when (failure.error) {
            is com.gy.githubclient.core.domain.network.ApiError.Unauthorized -> "登录态已失效，请重新登录"
            is com.gy.githubclient.core.domain.network.ApiError.NotFound -> "当前凭证无 Notifications 读取权限（可在权限体检核对）"
            else -> "通知加载失败，下拉或点击重试"
        }
}
