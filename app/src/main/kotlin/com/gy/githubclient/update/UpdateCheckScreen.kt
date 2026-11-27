/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.update

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.BuildConfig
import com.gy.githubclient.core.presentation.theme.LocalFrostColors
import org.koin.androidx.compose.koinViewModel

/** 检查更新页：手动检查 + 版本/摘要展示 + 应用内下载入口（v0.6.0 M5/M8 · M1-04 状态机可视化）。 */
@Composable
fun UpdateCheckScreen(
    onBack: () -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    viewModel: UpdateCheckViewModel = koinViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text("检查更新", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "当前版本 ${BuildConfig.VERSION_NAME}（versionCode ${BuildConfig.VERSION_CODE}）",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        UpdateStateCapsules(state)

        Spacer(Modifier.height(12.dp))

        PrereleaseSwitch(viewModel.includePrerelease, viewModel::setIncludePrerelease)

        Spacer(Modifier.height(12.dp))

        when (val s = state) {
            UpdateCheckUiState.Idle -> {
                Button(onClick = viewModel::checkNow, modifier = Modifier.fillMaxWidth()) {
                    Text("立即检查")
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "后台每 6 小时自动检查一次（304 条件请求，不消耗 API 配额）。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            UpdateCheckUiState.Checking -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.padding(8.dp))
                    Spacer(Modifier.height(0.dp).width(8.dp))
                    Text("正在检查…")
                }
            }

            is UpdateCheckUiState.UpToDate -> {
                Text("已是最新版本（${s.currentVersion}）")
                if (s.reason.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        s.reason,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is UpdateCheckUiState.Available -> {
                NewVersionCard(
                    state =
                        NewVersionCardState(
                            tagName = s.info.tagName,
                            sizeBytes = s.info.assetSize,
                            sha256 = s.info.sha256,
                            forced = s.forced,
                            reason = s.reason,
                        ),
                    onDownload = { viewModel.download(s.info) },
                    onSkip = if (s.forced) null else viewModel::skipCurrentVersion,
                )
            }

            is UpdateCheckUiState.Skipped -> {
                Text("已跳过 ${s.version}（按指纹记录，同一产物不再提醒）")
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = viewModel::checkNow) { Text("重新检查") }
            }

            is UpdateCheckUiState.Silent -> {
                Text(
                    "暂无可比较的更新信息：${s.reason}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = viewModel::checkNow) { Text("重试") }
            }

            is UpdateCheckUiState.Queued -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("已加入下载队列", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${s.fileName}\n下载完成后将自动校验 SHA-256 与 APK 签名指纹，任一不匹配即删除告警。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onOpenDownloads) { Text("前往下载管理 →") }
                    }
                }
            }

            is UpdateCheckUiState.Error -> {
                Text(s.message, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = viewModel::checkNow) { Text("重试") }
            }
        }
    }
}

/**
 * 更新状态机可视化（P-101 八态胶囊）。
 *
 * 状态色 + 文字双编码（色盲可用），当前态高亮；不可横向滚动截断。
 */
@Composable
private fun UpdateStateCapsules(state: UpdateCheckUiState) {
    val current = state.stateName()
    // 状态色一律取 Frost 令牌（单一色源，见 konsist 规则 14）
    val frost = LocalFrostColors.current
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        listOf(
            "Idle" to frost.stateNeutral,
            "Checking" to frost.stateInfo,
            "NoUpdate" to frost.stateSuccess,
            "OptionalUpdate" to frost.stateWarning,
            "ForcedUpdate" to frost.danger,
            "Unknown" to frost.stateNeutral,
            "Downloading" to frost.stateInfo,
            "ReadyToInstall" to frost.stateSuccess,
        ).forEach { (name, color) ->
            val active = name == current
            Surface(
                color = if (active) color else color.copy(alpha = 0.14f),
                shape = MaterialTheme.shapes.small,
            ) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (active) frost.onStateFill else color,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/** 当前 UI 状态对应的状态机名称。 */
private fun UpdateCheckUiState.stateName(): String =
    when (this) {
        UpdateCheckUiState.Idle -> "Idle"
        UpdateCheckUiState.Checking -> "Checking"
        is UpdateCheckUiState.UpToDate -> "NoUpdate"
        is UpdateCheckUiState.Available -> if (forced) "ForcedUpdate" else "OptionalUpdate"
        is UpdateCheckUiState.Skipped -> "NoUpdate"
        is UpdateCheckUiState.Silent -> "Unknown"
        is UpdateCheckUiState.Queued -> "Downloading"
        is UpdateCheckUiState.Error -> "Unknown"
    }

@Composable
private fun PrereleaseSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text("包含预发布版本", style = MaterialTheme.typography.titleSmall)
            Text(
                "开启后扫描最近 15 个 Release（含 prerelease），按 versionCode 取最新",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

/** 新版本卡片的数据（用数据类承载，避免长参数列表）。 */
private data class NewVersionCardState(
    val tagName: String,
    val sizeBytes: Long,
    val sha256: String?,
    val forced: Boolean,
    val reason: String,
)

@Composable
private fun NewVersionCard(
    state: NewVersionCardState,
    onDownload: () -> Unit,
    onSkip: (() -> Unit)?,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (state.forced) "必须更新：${state.tagName}" else "发现新版本：${state.tagName}",
                style = MaterialTheme.typography.titleMedium,
            )
            if (state.reason.isNotBlank()) {
                Text(state.reason, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(4.dp))
            if (state.sizeBytes > 0) {
                Text(
                    "APK 大小：%.1f MB".format(state.sizeBytes / BYTES_PER_MB),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                state.sha256?.let { "官方 SHA-256 前 16 位：${it.take(16)}…" }
                    ?: "官方摘要缺失：下载后将仅做 APK 签名指纹校验",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Text("应用内下载（自动校验）")
            }
            if (onSkip != null) {
                TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) {
                    Text("跳过此版（按指纹记录）")
                }
            } else {
                Text(
                    "主/次版本变化：需升级后才能继续使用，不提供跳过。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private const val BYTES_PER_MB = 1024f * 1024f
