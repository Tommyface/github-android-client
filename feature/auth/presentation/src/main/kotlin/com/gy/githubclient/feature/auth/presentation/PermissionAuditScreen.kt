/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.core.audit.AuditRecord
import com.gy.githubclient.core.permission.ScopeStatus
import com.gy.githubclient.core.presentation.theme.FrostCard
import com.gy.githubclient.core.presentation.theme.FrostDimens
import com.gy.githubclient.core.presentation.theme.FrostTopBar
import com.gy.githubclient.core.presentation.theme.LocalFrostColors
import com.gy.githubclient.core.presentation.theme.LocalFrostContentInsets
import com.gy.githubclient.feature.auth.domain.model.AuthMethod
import com.gy.githubclient.feature.auth.domain.model.Capability
import com.gy.githubclient.feature.auth.domain.model.PermissionReport
import com.gy.githubclient.feature.auth.domain.model.RateLimitInfo
import org.koin.androidx.compose.koinViewModel

/**
 * 权限体检页：当前凭证能做什么 / 缺什么。
 * GitHub 的权限由凭证本身决定，App 只负责如实展示与引导，不支持在线改权限。
 *
 * v0.19.0 Frost：通栏由本页绘制（返回 + 重新体检），正文全部落在玻璃卡片里，
 * 内容可以滚到通栏下方。
 */
@Composable
fun PermissionAuditScreen(
    onBack: () -> Unit = {},
    viewModel: PermissionAuditViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val auditEntries by viewModel.auditEntries.collectAsStateWithLifecycle()
    val report = uiState.report

    val frost = LocalFrostColors.current
    val insets = LocalFrostContentInsets.current

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = FrostDimens.PagePadding,
                    end = FrostDimens.PagePadding,
                    top = insets.top,
                    bottom = insets.bottom + 32.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(FrostDimens.CardGap),
        ) {
            item(key = "audit-note") {
                FrostCard {
                    Text(
                        "GitHub 凭证的权限由创建/授权时决定；本页只展示当前凭证实际可用范围。" +
                            "修改权限请在 GitHub 网页端调整。",
                        style = MaterialTheme.typography.labelSmall,
                        color = frost.textSecondary,
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    item(key = "audit-loading") {
                        Column(
                            Modifier.fillMaxWidth().padding(48.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) { CircularProgressIndicator() }
                    }
                }

                uiState.errorMessage != null -> {
                    item(key = "audit-error") {
                        FrostCard {
                            Text(
                                "体检失败",
                                style = MaterialTheme.typography.titleSmall,
                                color = frost.danger,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                uiState.errorMessage.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = frost.textSecondary,
                            )
                        }
                    }
                }

                report != null -> {
                    item(key = "audit-summary") { ReportSummary(report) }
                    report.rateLimit?.let { rateLimit ->
                        item(key = "audit-ratelimit") { RateLimitCard(rateLimit) }
                    }
                    if (report.scopeStatuses.isNotEmpty()) {
                        item(key = "audit-scope") {
                            ScopeGateCard(report.scopeStatuses, report.missingScopes)
                        }
                    }
                    items(report.capabilities, key = { "cap_${it.name}" }) { capability ->
                        CapabilityCard(capability)
                    }
                    item(key = "audit-log") {
                        AuditLogCard(auditEntries, onClear = viewModel::clearAudit)
                    }
                }
            }
        }

        FrostTopBar(
            title = "权限体检",
            subtitle =
                report?.let { "${it.grantedCount}/${it.capabilities.size} 项功能可用" }
                    ?: "只读展示 · 不支持在线改权限",
            onBack = onBack,
            largeTitle = false,
            actions = {
                TextButton(onClick = viewModel::refresh) { Text("重新体检") }
            },
        )
    }
}

/** Scope 门控卡片：写能力逐项可用性与缺权修复指引。 */
@Composable
private fun ScopeGateCard(
    statuses: List<ScopeStatus>,
    missingScopes: List<String>,
) {
    val frost = LocalFrostColors.current
    FrostCard {
        Text("写操作授权（scope）", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        statuses.forEach { status ->
            Text(
                (if (status.granted) "✓ " else "✗ ") + status.feature.title,
                style = MaterialTheme.typography.bodySmall,
                color = if (status.granted) frost.textPrimary else frost.danger,
            )
            Text(
                status.feature.purpose,
                style = MaterialTheme.typography.labelSmall,
                color = frost.textSecondary,
            )
            Spacer(Modifier.height(4.dp))
        }
        if (missingScopes.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            Text(
                "缺少：${missingScopes.joinToString("、")}",
                style = MaterialTheme.typography.bodySmall,
                color = frost.danger,
            )
            Text(
                "OAuth 用户请退出登录后重新授权；classic PAT 请编辑 Token 勾选缺失 scope；" +
                    "fine-grained PAT 请在仓库/账号权限中开启对应能力。",
                style = MaterialTheme.typography.labelSmall,
                color = frost.textSecondary,
            )
        } else {
            Text(
                "推荐 scope 已齐备，写操作不会因缺权被拒。",
                style = MaterialTheme.typography.labelSmall,
                color = frost.primaryInk,
            )
        }
    }
}

/** 核心 API 限流余量卡片（v0.5.3 M2）：剩余额度与重置时间。 */
@Composable
private fun RateLimitCard(rateLimit: RateLimitInfo) {
    val frost = LocalFrostColors.current
    val resetText =
        remember(rateLimit.resetEpochSeconds) {
            java.text
                .SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(rateLimit.resetEpochSeconds * 1000))
        }
    val ratio = if (rateLimit.limit > 0) rateLimit.remaining.toFloat() / rateLimit.limit else 0f
    FrostCard {
        Text("API 限流余量", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            "核心 API：剩余 ${rateLimit.remaining} / ${rateLimit.limit} 次，$resetText 重置",
            style = MaterialTheme.typography.bodySmall,
            color = frost.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            when {
                ratio <= 0.1f -> "额度紧张：部分请求可能被限流，稍后自动恢复"
                ratio <= 0.3f -> "额度偏低：大文件下载与搜索建议错峰使用"
                else -> "额度充足"
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (ratio <= 0.1f) frost.danger else frost.textSecondary,
        )
    }
}

@Composable
private fun ReportSummary(report: PermissionReport) {
    val frost = LocalFrostColors.current
    val methodLabel =
        when (report.method) {
            AuthMethod.OAUTH -> "OAuth 账号授权"
            AuthMethod.PAT -> "Personal Access Token"
            null -> "未知"
        }
    FrostCard {
        Text("凭证：$methodLabel", style = MaterialTheme.typography.titleSmall)
        report.login?.let {
            Spacer(Modifier.height(2.dp))
            Text("账号：$it", style = MaterialTheme.typography.bodySmall, color = frost.textSecondary)
        }
        if (report.scopes.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("授予的 scope", style = MaterialTheme.typography.labelMedium, color = frost.textSecondary)
            Spacer(Modifier.height(4.dp))
            RowOrFlow(report.scopes)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "已确认可用 ${report.grantedCount}/${report.capabilities.size} 项功能面",
            style = MaterialTheme.typography.bodySmall,
            color = frost.primaryInk,
        )
    }
}

@Composable
private fun RowOrFlow(scopes: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        scopes.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { scope -> AssistChip(onClick = {}, label = { Text(scope) }) }
            }
        }
    }
}

/** 最近写操作审计：动作、目标、结果与请求 id（便于向 GitHub 排查）。 */
@Composable
private fun AuditLogCard(
    entries: List<AuditRecord>,
    onClear: () -> Unit,
) {
    val frost = LocalFrostColors.current
    FrostCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("最近写操作", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            if (entries.isNotEmpty()) {
                TextButton(onClick = onClear) { Text("清空") }
            }
        }
        if (entries.isEmpty()) {
            Text(
                "还没有写操作记录。Star / Watch / 关注 / 通知忽略都会记录在这里。",
                style = MaterialTheme.typography.labelSmall,
                color = frost.textSecondary,
            )
            return@FrostCard
        }
        entries.take(AUDIT_PREVIEW_LIMIT).forEach { entry ->
            Spacer(Modifier.height(6.dp))
            Text(
                (if (entry.success) "✓ " else "✗ ") + entry.action + " · " + entry.target,
                style = MaterialTheme.typography.bodySmall,
                color = if (entry.success) frost.textPrimary else frost.danger,
            )
            Text(
                buildString {
                    append(formatAuditTime(entry.timestampMillis))
                    entry.statusCode?.let { append(" · HTTP $it") }
                    if (entry.attempts > 1) append(" · 尝试 ${entry.attempts} 次")
                    entry.message?.let { append(" · $it") }
                },
                style = MaterialTheme.typography.labelSmall,
                color = frost.textSecondary,
            )
            entry.requestId?.let {
                Text(
                    "request-id：$it",
                    style = MaterialTheme.typography.labelSmall,
                    color = frost.textTertiary,
                )
            }
        }
    }
}

private const val AUDIT_PREVIEW_LIMIT = 20

private fun formatAuditTime(timestampMillis: Long): String =
    java.text
        .SimpleDateFormat("MM-dd HH:mm:ss", java.util.Locale.getDefault())
        .format(java.util.Date(timestampMillis))

@Composable
private fun CapabilityCard(capability: Capability) {
    val frost = LocalFrostColors.current
    FrostCard {
        Text(
            (if (capability.granted) "✓ " else "✗ ") + capability.name,
            style = MaterialTheme.typography.titleSmall,
            color = if (capability.granted) frost.textPrimary else frost.danger,
        )
        Spacer(Modifier.height(2.dp))
        Text(capability.features, style = MaterialTheme.typography.bodySmall, color = frost.textSecondary)
        Spacer(Modifier.height(2.dp))
        Text(
            "依据：${capability.basis}",
            style = MaterialTheme.typography.labelSmall,
            color = frost.textTertiary,
        )
    }
}
