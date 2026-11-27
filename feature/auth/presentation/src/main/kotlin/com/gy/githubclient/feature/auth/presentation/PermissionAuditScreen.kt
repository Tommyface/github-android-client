/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.gy.githubclient.core.presentation.theme.LocalFrostColors
import com.gy.githubclient.core.presentation.theme.FrostCard
import com.gy.githubclient.feature.auth.domain.model.AuthMethod
import com.gy.githubclient.feature.auth.domain.model.Capability
import com.gy.githubclient.feature.auth.domain.model.PermissionReport
import com.gy.githubclient.feature.auth.domain.model.RateLimitInfo
import org.koin.androidx.compose.koinViewModel

/**
 * 权限体检页：当前凭证能做什么 / 缺什么。
 * GitHub 的权限由凭证本身决定，App 只负责如实展示与引导，不支持在线改权限。
 */
@Composable
fun PermissionAuditScreen(
    onBack: () -> Unit = {},
    viewModel: PermissionAuditViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text("权限体检", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::refresh) { Text("重新体检") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "GitHub 凭证的权限由创建/授权时决定；本页只展示当前凭证实际可用范围。修改权限请在 GitHub 网页端调整。",
            style = MaterialTheme.typography.labelSmall,
            color = LocalFrostColors.current.textTertiary,
        )
        Spacer(Modifier.height(12.dp))

        when {
            uiState.isLoading -> {
                Column(
                    Modifier.fillMaxWidth().padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) { CircularProgressIndicator() }
            }

            uiState.errorMessage != null -> {
                FrostCard {
                    Column(Modifier.padding(16.dp)) {
                        Text("体检失败", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(uiState.errorMessage.orEmpty(), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            else -> {
                val report = uiState.report ?: return@Column
                ReportSummary(report)
                Spacer(Modifier.height(12.dp))
                report.rateLimit?.let { rateLimit ->
                    RateLimitCard(rateLimit)
                    Spacer(Modifier.height(12.dp))
                }
                report.capabilities.forEach { capability ->
                    CapabilityCard(capability)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

/** 核心 API 限流余量卡片（v0.5.3 M2）：剩余额度与重置时间。 */
@Composable
private fun RateLimitCard(rateLimit: RateLimitInfo) {
    val resetText =
        remember(rateLimit.resetEpochSeconds) {
            java.text
                .SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(rateLimit.resetEpochSeconds * 1000))
        }
    val ratio = if (rateLimit.limit > 0) rateLimit.remaining.toFloat() / rateLimit.limit else 0f
    FrostCard {
        Column(Modifier.padding(16.dp)) {
            Text("API 限流余量", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "核心 API：剩余 ${rateLimit.remaining} / ${rateLimit.limit} 次，$resetText 重置",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                when {
                    ratio <= 0.1f -> "额度紧张：部分请求可能被限流，稍后自动恢复"
                    ratio <= 0.3f -> "额度偏低：大文件下载与搜索建议错峰使用"
                    else -> "额度充足"
                },
                style = MaterialTheme.typography.labelSmall,
                color = LocalFrostColors.current.textTertiary,
            )
        }
    }
}

@Composable
private fun ReportSummary(report: PermissionReport) {
    val methodLabel =
        when (report.method) {
            AuthMethod.OAUTH -> "OAuth 账号授权"
            AuthMethod.PAT -> "Personal Access Token"
            null -> "未知"
        }
    FrostCard {
        Column(Modifier.padding(16.dp)) {
            Text("凭证：$methodLabel", style = MaterialTheme.typography.titleSmall)
            report.login?.let {
                Spacer(Modifier.height(2.dp))
                Text("账号：$it", style = MaterialTheme.typography.bodySmall)
            }
            if (report.scopes.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("授予的 scope", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                RowOrFlow(report.scopes)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "已确认可用 ${report.grantedCount}/${report.capabilities.size} 项功能面",
                style = MaterialTheme.typography.bodySmall,
                color = LocalFrostColors.current.primaryInk,
            )
        }
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

@Composable
private fun CapabilityCard(capability: Capability) {
    // 设计系统的卡片没有 colors 参数：可用性用**文字与符号**表达（✓ / ✗），
    // 不再靠底色区分——这也更符合深色主题与色盲友好原则。
    FrostCard {
        Column(Modifier.padding(12.dp)) {
            Text(
                (if (capability.granted) "✓ " else "✗ ") + capability.name,
                style = MaterialTheme.typography.titleSmall,
                color =
                    if (capability.granted) {
                        LocalFrostColors.current.textPrimary
                    } else {
                        LocalFrostColors.current.danger
                    },
            )
            Spacer(Modifier.height(2.dp))
            Text(capability.features, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(2.dp))
            Text(
                "依据：${capability.basis}",
                style = MaterialTheme.typography.labelSmall,
                color = LocalFrostColors.current.textTertiary,
            )
        }
    }
}
