/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.profile.domain.model.GitHubNotification
import com.gy.githubclient.feature.profile.domain.usecase.ListNotificationsUseCase
import com.gy.githubclient.feature.profile.domain.usecase.MarkNotificationReadUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 通知加载失败的原因分类。
 *
 * 只给"分类"，不给文案：ViewModel 不产出中文字符串，
 * 界面层（`NotificationsError.asText()`）负责把它映射成 `strings.xml` 里的文案。
 */
enum class NotificationsError {
    /** 401：登录态失效。 */
    UNAUTHORIZED,

    /** 403/404：当前凭证（多为 fine-grained PAT）没有 Notifications 权限。 */
    FORBIDDEN,

    /** 网络、限流、5xx 等其余失败。 */
    UNKNOWN,
}

/** 通知页 UI 状态。 */
data class NotificationsUiState(
    val isLoading: Boolean = false,
    val items: List<GitHubNotification> = emptyList(),
    /** 已本地标记已读的线程 id（乐观更新）。 */
    val locallyReadIds: Set<Long> = emptySet(),
    val error: NotificationsError? = null,
) {
    /** 展示中的未读数：本地标记已读的条目立即从未读计数里移除。 */
    val unreadCount: Int get() = items.count { it.unread && it.id !in locallyReadIds }
}

/**
 * 通知中心 ViewModel（v0.6.0 M7）：
 * 进入页面拉取未读通知；点击条目标记已读（乐观更新 + 后台 PATCH）并交给外部打开。
 */
class NotificationsViewModel(
    private val listNotifications: ListNotificationsUseCase,
    private val markRead: MarkNotificationReadUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_uiState.value.isLoading) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            _uiState.value =
                when (val result = listNotifications(all = false)) {
                    is NetworkResult.Success ->
                        _uiState.value.copy(isLoading = false, items = result.data)

                    is NetworkResult.Failure ->
                        _uiState.value.copy(
                            isLoading = false,
                            error = errorKindFor(result),
                        )
                }
        }
    }

    /**
     * 点击一条未读通知：立即本地标记已读，同时后台 PATCH 线程端点。
     * 返回该通知的主题跳转 URL（无则 null）。
     */
    fun onOpen(notification: GitHubNotification): String? {
        if (notification.unread && notification.id !in _uiState.value.locallyReadIds) {
            _uiState.value = _uiState.value.copy(locallyReadIds = _uiState.value.locallyReadIds + notification.id)
            viewModelScope.launch { markRead(notification.threadId) }
        }
        return notification.subject.url
    }

    /** 失败原因归类（保持 v0.6.0 的三分类语义：401 / 无权限 / 其余）。 */
    private fun errorKindFor(failure: NetworkResult.Failure): NotificationsError {
        val error = failure.error
        return when (error) {
            is ApiError.Unauthorized -> NotificationsError.UNAUTHORIZED
            // GitHub 对"凭证无权访问该端点"返回 404（而非 403），故 NotFound 归为无权限。
            is ApiError.NotFound -> NotificationsError.FORBIDDEN
            else -> NotificationsError.UNKNOWN
        }
    }
}
