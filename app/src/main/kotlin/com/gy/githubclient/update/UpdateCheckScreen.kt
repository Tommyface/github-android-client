/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.update

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
import org.koin.androidx.compose.koinViewModel

/** 检查更新页：手动检查 + 版本/摘要展示 + 应用内下载入口（v0.6.0 M5/M8）。 */
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

            is UpdateCheckUiState.UpToDate -> Text("已是最新版本（${s.currentVersion}）")

            is UpdateCheckUiState.Available -> {
                NewVersionCard(
                    tagName = s.info.tagName,
                    sizeBytes = s.info.assetSize,
                    sha256 = s.info.sha256,
                    onDownload = { viewModel.download(s.info) },
                )
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

@Composable
private fun NewVersionCard(
    tagName: String,
    sizeBytes: Long,
    sha256: String?,
    onDownload: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("发现新版本：$tagName", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (sizeBytes > 0) {
                Text(
                    "APK 大小：%.1f MB".format(sizeBytes / BYTES_PER_MB),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                sha256?.let { "官方 SHA-256 前 16 位：${it.take(16)}…" }
                    ?: "官方摘要缺失：下载后将仅做 APK 签名指纹校验",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                Text("应用内下载（自动校验）")
            }
        }
    }
}

private const val BYTES_PER_MB = 1024f * 1024f
