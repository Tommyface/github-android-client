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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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

/** 通知中心页（v0.6.0 M7）：未读列表 + 点击标记已读并跳转主题。 */
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
                "通知中心（未读 ${state.unreadCount}）",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(8.dp))

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
                            locallyRead = notification.id in state.locallyReadIds,
                            onOpen = {
                                val subjectUrl = viewModel.onOpen(notification)
                                // subject.url 是 API 地址；浏览器打开转 html 形式不可得，直接外抛 API url 由 GitHub 重定向。
                                runCatching { uriHandler.openUri(subjectUrl ?: return@NotificationCard) }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(
    notification: GitHubNotification,
    locallyRead: Boolean,
    onOpen: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
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
