/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.presentation

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
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
import com.gy.githubclient.feature.download.domain.model.DownloadStatus
import com.gy.githubclient.feature.download.domain.model.DownloadTask
import org.koin.androidx.compose.koinViewModel
import java.io.File

/** 下载管理页：任务列表 + 进度 + 取消/重试/打开/删除 + 镜像设置入口。 */
@Composable
fun DownloadScreen(
    onOpenMirrorSettings: () -> Unit = {},
    viewModel: DownloadViewModel = koinViewModel(),
) {
    val tasks by viewModel.tasks.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("下载管理", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = onOpenMirrorSettings) { Text("镜像设置") }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "进行中 ${stats.active} · 已完成 ${stats.done} · 失败 ${stats.failed}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (stats.done + stats.failed > 0) {
            TextButton(onClick = viewModel::onClearCompleted) { Text("清空已完成记录") }
        }
        Spacer(Modifier.height(8.dp))

        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "暂无下载任务\n在「发现」中选择 Release 资产即可开始下载",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Column
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(task.request.fileName, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                task.request.repoOwner?.let { "$it/${task.request.repoName} · ${task.request.releaseTag}" }
                    ?: task.request.url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                    )
                }

                DownloadStatus.DONE -> {
                    Text(
                        "✓ 下载完成 · ${formatSize(task.bytesDownloaded)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                DownloadStatus.FAILED -> {
                    Text(
                        "✗ 失败：${task.error ?: "未知错误"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                DownloadStatus.CANCELLED -> {
                    Text("已取消", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                DownloadStatus.PAUSED -> {
                    Text("已暂停", style = MaterialTheme.typography.labelSmall)
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
