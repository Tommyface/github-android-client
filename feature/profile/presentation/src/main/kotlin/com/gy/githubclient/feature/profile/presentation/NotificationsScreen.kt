/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.feature.profile.domain.model.GitHubNotification
import org.koin.androidx.compose.koinViewModel

/**
 * 通知中心页（v0.6.0 M7；批量能力对齐 githup）：
 * 未读列表 + 单条已读 + 多选批量已读 + 一键全部已读。
 * subject.url 是 REST 地址，统一转成网页地址再打开。
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit = {},
    viewModel: NotificationsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text(
                text =
                    if (state.selectionMode) {
                        "已选 ${state.selectedIds.size} 条"
                    } else {
                        "通知中心（未读 ${state.unreadCount}）"
                    },
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            if (state.selectionMode) {
                TextButton(onClick = viewModel::toggleSelectionMode) { Text("取消") }
            } else {
                if (state.items.isNotEmpty()) {
                    TextButton(onClick = viewModel::toggleSelectionMode) { Text("批量") }
                    TextButton(onClick = viewModel::markAllRead, enabled = state.unreadCount > 0) {
                        Text("全部已读")
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))

        if (state.selectionMode) {
            Button(
                onClick = viewModel::markSelectedRead,
                enabled = state.selectedIds.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("标记所选已读（${state.selectedIds.size}）")
            }
            Spacer(Modifier.height(8.dp))
        }

        when {
            state.isLoading && state.items.isEmpty() -> {
                Row(
                    Modifier.fillMaxWidth().padding(top = 48.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            state.errorMessage?.let { message -> message.isNotEmpty() } == true -> {
                Text(
                    state.errorMessage.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = viewModel::refresh) { Text("重试") }
            }

            state.items.isEmpty() -> {
                Text(
                    "没有未读通知",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 24.dp),
                )
            }

            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(state.items, key = { it.id }) { notification ->
                        NotificationCard(
                            notification = notification,
                            selection =
                                NotificationCardSelection(
                                    locallyRead = notification.id in state.locallyReadIds,
                                    selectionMode = state.selectionMode,
                                    selected = notification.id in state.selectedIds,
                                    ignored = notification.id in state.ignoredIds,
                                ),
                            onOpen = {
                                val subjectUrl = viewModel.onOpen(notification)
                                if (subjectUrl != null) {
                                    runCatching { uriHandler.openUri(subjectUrl) }
                                }
                            },
                            onToggleSelected = { viewModel.toggleSelected(notification.id) },
                            onToggleIgnore = { viewModel.toggleIgnore(notification) },
                        )
                    }
                }
            }
        }
    }
}

private data class NotificationCardSelection(
    val locallyRead: Boolean,
    val selectionMode: Boolean,
    val selected: Boolean,
    val ignored: Boolean,
)

@Composable
private fun NotificationCard(
    notification: GitHubNotification,
    selection: NotificationCardSelection,
    onOpen: () -> Unit,
    onToggleSelected: () -> Unit,
    onToggleIgnore: () -> Unit,
) {
    val locallyRead = selection.locallyRead
    val selectionMode = selection.selectionMode
    val selected = selection.selected
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = if (selectionMode) onToggleSelected else onOpen),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (notification.unread && !locallyRead) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    },
            ),
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) {
                    Checkbox(checked = selected, onCheckedChange = { onToggleSelected() })
                    Spacer(Modifier.size(4.dp))
                }
                if (notification.unread && !locallyRead) {
                    androidx.compose.material3.Surface(
                        modifier = Modifier.size(8.dp),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = MaterialTheme.colorScheme.primary,
                    ) { }
                    Spacer(Modifier.size(6.dp))
                }
                Text(
                    "${subjectTypeLabel(notification.subject.type)} · ${notification.repoFullName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                notification.subject.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
            )
            notification.updatedAt?.let {
                Spacer(Modifier.height(4.dp))
                Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = onToggleIgnore, enabled = !selectionMode) {
                    Text(if (selection.ignored) "恢复通知" else "忽略此线程")
                }
            }
        }
    }
}

private fun subjectTypeLabel(type: String): String =
    when (type) {
        "Issue" -> "Issue"
        "PullRequest" -> "PR"
        "Release" -> "Release"
        "Discussion" -> "讨论"
        "CheckSuite" -> "CI"
        else -> type.ifBlank { "通知" }
    }
