/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.core.presentation.theme.FrostCard
import com.gy.githubclient.core.presentation.theme.FrostDimens
import com.gy.githubclient.core.presentation.theme.LocalFrostColors
import com.gy.githubclient.feature.profile.domain.model.GitHubNotification
import org.koin.androidx.compose.koinViewModel

/**
 * 通知中心页（v0.6.0 M7）。
 *
 * M0-18 批次 2：文案全部迁 `strings.xml`、配色改用 Frost 令牌、补齐无障碍语义
 * （卡片朗读文本 + 未读状态可听 + 错误/空态 liveRegion），不再有裸色值与中文字面量。
 */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit = {},
    viewModel: NotificationsViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    val frost = LocalFrostColors.current
    // 先取出可空错误：`state` 是委托属性，直接对它做智能转换不成立。
    val error = state.error

    Column(modifier = Modifier.fillMaxSize().padding(FrostDimens.PagePadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(
                text = stringResource(R.string.title, state.unreadCount),
                style = MaterialTheme.typography.headlineSmall,
                color = frost.textPrimary,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(FrostDimens.SectionGap))

        when {
            state.isLoading && state.items.isEmpty() -> LoadingBlock()

            error != null -> ErrorBlock(error = error, onRetry = viewModel::refresh)

            state.items.isEmpty() -> EmptyBlock()

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

/** 首屏加载态：居中进度圈，读屏通过 progressBarRangeInfo 自行播报。 */
@Composable
private fun LoadingBlock() {
    Row(
        Modifier.fillMaxWidth().padding(top = 48.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator()
    }
}

/** 错误态：文案由语义错误类型映射，礼貌级 liveRegion 保证出错时读屏会播报。 */
@Composable
private fun ErrorBlock(
    error: NotificationsError,
    onRetry: () -> Unit,
) {
    val frost = LocalFrostColors.current
    Column(Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
        Text(
            text = error.asText(),
            style = MaterialTheme.typography.bodyMedium,
            color = frost.danger,
        )
        Spacer(Modifier.height(8.dp))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

/** 空态：没有未读通知也要让读屏播报"列表为空"这件事。 */
@Composable
private fun EmptyBlock() {
    val frost = LocalFrostColors.current
    Text(
        text = stringResource(R.string.empty),
        style = MaterialTheme.typography.bodyMedium,
        color = frost.textSecondary,
        modifier =
            Modifier
                .padding(top = 24.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/** 语义错误类型 → 界面文案（"怎么说"归界面层，ViewModel 不产出字符串）。 */
@Composable
private fun NotificationsError.asText(): String =
    when (this) {
        NotificationsError.UNAUTHORIZED -> stringResource(R.string.error_unauthorized)
        NotificationsError.FORBIDDEN -> stringResource(R.string.error_forbidden)
        NotificationsError.UNKNOWN -> stringResource(R.string.error_unknown)
    }

@Composable
private fun NotificationCard(
    notification: GitHubNotification,
    locallyRead: Boolean,
    onOpen: () -> Unit,
) {
    val frost = LocalFrostColors.current
    val unread = notification.unread && !locallyRead
    val typeLabel = subjectTypeLabel(notification.subject.type)
    // 未读是这张卡最重要的状态：整卡合并成一个语义节点，朗读时带上"未读"。
    val cardDescription =
        stringResource(
            if (unread) R.string.card_desc_unread else R.string.card_desc,
            typeLabel,
            notification.repoFullName,
        )

    FrostCard(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = cardDescription },
        onClick = onOpen,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (unread) {
                Box(
                    modifier =
                        Modifier
                            .size(8.dp)
                            .background(frost.primary, CircleShape)
                            // 圆点是纯装饰：未读状态已经在朗读文本里，不能重复播报。
                            .clearAndSetSemantics {},
                )
                Spacer(Modifier.size(6.dp))
            }
            Text(
                text = "$typeLabel · ${notification.repoFullName}",
                style = MaterialTheme.typography.labelSmall,
                color = frost.textTertiary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = notification.subject.title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (unread) frost.textPrimary else frost.textSecondary,
            maxLines = 2,
        )
        notification.updatedAt?.let { updated ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = updated,
                style = MaterialTheme.typography.labelSmall,
                color = frost.textTertiary,
            )
        }
    }
}

/** 通知主题类型标签：技术缩写保持原样，需要翻译的只有 Discussion 与兜底文案。 */
@Composable
private fun subjectTypeLabel(type: String): String =
    when (type) {
        "Issue" -> "Issue"
        "PullRequest" -> "PR"
        "Release" -> "Release"
        "Discussion" -> stringResource(R.string.subject_discussion)
        "CheckSuite" -> "CI"
        else -> if (type.isBlank()) stringResource(R.string.subject_fallback) else type
    }
