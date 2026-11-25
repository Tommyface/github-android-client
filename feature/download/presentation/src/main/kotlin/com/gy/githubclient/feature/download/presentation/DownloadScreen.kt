/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.core.presentation.theme.FrostCard
import com.gy.githubclient.core.presentation.theme.FrostDimens
import com.gy.githubclient.core.presentation.theme.FrostTopBar
import com.gy.githubclient.core.presentation.theme.LocalFrostColors
import com.gy.githubclient.core.presentation.theme.LocalFrostContentInsets
import com.gy.githubclient.feature.download.domain.model.DownloadStatus
import com.gy.githubclient.feature.download.domain.model.DownloadTask
import org.koin.androidx.compose.koinViewModel
import java.io.File

/**
 * 下载管理页：任务列表 + 进度 + 取消/重试/打开/删除 + 镜像设置入口。
 *
 * v0.19.0 Frost：标题与统计迁入顶部通栏，任务卡片改为玻璃卡片，
 * 内容可滚动到通栏下方。
 */
@Composable
fun DownloadScreen(
    onBack: () -> Unit = {},
    onOpenMirrorSettings: () -> Unit = {},
    viewModel: DownloadViewModel = koinViewModel(),
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val context = LocalContext.current

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
            item(key = "download_stats") {
                FrostCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "进行中 ${stats.active} · 已完成 ${stats.done} · 失败 ${stats.failed}",
                            style = MaterialTheme.typography.labelMedium,
                            color = frost.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        if (stats.done + stats.failed > 0) {
                            TextButton(onClick = viewModel::onClearCompleted) { Text("清空已完成") }
                        }
                    }
                }
            }

            if (tasks.isEmpty()) {
                item(key = "download_empty") {
                    Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                        Text(
                            "暂无下载任务\n在「发现」中选择 Release 资产即可开始下载",
                            style = MaterialTheme.typography.bodyMedium,
                            color = frost.textSecondary,
                        )
                    }
                }
            } else {
                items(tasks, key = { it.id }) { task ->
                    DownloadTaskCard(
                        task = task,
                        onCancel = { viewModel.onCancel(task.id) },
                        onRetry = { viewModel.onRetry(task.id) },
                        onRemove = { viewModel.onRemove(task.id) },
                        onOpen = { task.filePath?.let { openFile(context, it, task.request.fileName) } },
                    )
                }
            }
        }

        FrostTopBar(
            title = "下载管理",
            subtitle = "任务进度 · 镜像线路 · 本地文件",
            onBack = onBack,
            largeTitle = false,
            actions = {
                TextButton(onClick = onOpenMirrorSettings) { Text("镜像设置") }
            },
        )
    }
}

@Composable
@Suppress("CyclomaticComplexMethod")
private fun DownloadTaskCard(
    task: DownloadTask,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onRemove: () -> Unit,
    onOpen: () -> Unit,
) {
    val frost = LocalFrostColors.current
    FrostCard {
        Text(task.request.fileName, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(2.dp))
        Text(
            task.request.repoOwner?.let { "$it/${task.request.repoName} · ${task.request.releaseTag}" }
                ?: task.request.url,
            style = MaterialTheme.typography.labelSmall,
            color = frost.textTertiary,
            maxLines = 1,
        )
        Spacer(Modifier.height(8.dp))

        when (task.status) {
            DownloadStatus.DOWNLOADING,
            DownloadStatus.QUEUED,
            -> {
                LinearProgressIndicator(
                    progress = { (task.safePercent ?: 0) / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "${task.status.label} · ${task.safePercent ?: 0}%${task.mirrorName?.let { " · $it" } ?: ""}",
                    style = MaterialTheme.typography.labelSmall,
                    color = frost.textSecondary,
                )
            }

            DownloadStatus.DONE -> {
                Text(
                    "✓ 下载完成 · ${formatSize(task.bytesDownloaded)}",
                    style = MaterialTheme.typography.labelMedium,
                    color = frost.primaryInk,
                )
            }

            DownloadStatus.FAILED -> {
                Text(
                    "✗ 失败：${task.error ?: "未知错误"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = frost.danger,
                )
            }

            DownloadStatus.CANCELLED -> {
                Text(
                    "已取消",
                    style = MaterialTheme.typography.labelSmall,
                    color = frost.textSecondary,
                )
            }

            DownloadStatus.PAUSED -> {
                Text("已暂停", style = MaterialTheme.typography.labelSmall, color = frost.textSecondary)
            }
        }
        Spacer(Modifier.height(6.dp))

        Row {
            when (task.status) {
                DownloadStatus.DOWNLOADING,
                DownloadStatus.QUEUED,
                -> TextButton(onClick = onCancel) { Text("取消") }

                DownloadStatus.FAILED,
                DownloadStatus.CANCELLED,
                -> {
                    TextButton(onClick = onRetry) { Text("重试") }
                    TextButton(onClick = onRemove) { Text("删除") }
                }

                DownloadStatus.DONE -> {
                    TextButton(onClick = onOpen) { Text("打开") }
                    TextButton(onClick = onRemove) { Text("删除记录") }
                }

                DownloadStatus.PAUSED -> TextButton(onClick = onRetry) { Text("继续") }
            }
        }
    }
}

private fun openFile(
    context: Context,
    path: String,
    fileName: String,
) {
    runCatching {
        val file = File(path)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime =
            when (fileName.substringAfterLast('.', "").lowercase()) {
                "apk" -> "application/vnd.android.package-archive"
                "zip" -> "application/zip"
                "txt", "md" -> "text/plain"
                else -> "*/*"
            }
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

private fun formatSize(bytes: Long): String =
    when {
        bytes <= 0 -> "0 B"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }

private val DownloadStatus.label: String
    get() =
        when (this) {
            DownloadStatus.QUEUED -> "排队中"
            DownloadStatus.DOWNLOADING -> "下载中"
            DownloadStatus.PAUSED -> "已暂停"
            DownloadStatus.DONE -> "完成"
            DownloadStatus.FAILED -> "失败"
            DownloadStatus.CANCELLED -> "取消"
        }
