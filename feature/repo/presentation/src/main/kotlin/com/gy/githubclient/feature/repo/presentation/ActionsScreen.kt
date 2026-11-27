/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.core.presentation.theme.FrostCard
import com.gy.githubclient.core.presentation.theme.LocalFrostColors
import org.koin.androidx.compose.koinViewModel

/**
 * Actions Run 列表页（v0.14.0 A3）：只读展示 workflow run 列表 + 状态/结论，
 * 点条目跳浏览器打开 run 详情（日志在网页查看，不臆造日志 API）。
 */
@Composable
fun ActionsScreen(
    owner: String,
    name: String,
    onBack: () -> Unit = {},
    viewModel: ActionsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(owner, name) { viewModel.init(owner, name) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text("$owner/$name · Actions", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
        }

        uiState.error?.let { error ->
            FrostCard {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.dismissError() }) { Text("关闭") }
                }
            }
        }

        if (uiState.isLoading) {
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (!uiState.isLoading && uiState.runs.isEmpty() && uiState.error == null) {
                item {
                    Text(
                        "该仓库暂无 Actions 运行记录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalFrostColors.current.textTertiary,
                    )
                }
            }
            items(uiState.runs, key = { it.id }) { run ->
                FrostCard(
                    onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(run.htmlUrl)))
                    },
                ) {
                    Column(
                        Modifier
                            .padding(12.dp)
                            // 这张卡片的动作是"在浏览器打开"，正文里没有这句，
                            // 读屏用户需要被明确告知点了会发生什么。
                            .semantics {
                                contentDescription =
                                    "在浏览器中打开第 ${run.runNumber} 次运行 ${run.name}"
                            },
                    ) {
                        Text("#${run.runNumber} ${run.name}", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${runStatusLabel(run.normalizedStatus)} · ${conclusionLabel(run.normalizedConclusion)} · ${run.event}",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalFrostColors.current.textTertiary,
                        )
                        Text(
                            "${run.headBranch} @ ${run.shortSha}",
                            style = MaterialTheme.typography.labelSmall,
                            color = LocalFrostColors.current.textTertiary,
                        )
                    }
                }
            }
            if (uiState.hasMore) {
                item {
                    TextButton(
                        onClick = { viewModel.loadMore() },
                        enabled = !uiState.isLoadingMore,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (uiState.isLoadingMore) "加载中…" else "加载更多")
                    }
                }
            }
        }
    }
}

/** run 状态中文标签（纯函数）。 */
private fun runStatusLabel(status: com.gy.githubclient.feature.repo.domain.model.RunStatus): String =
    when (status) {
        com.gy.githubclient.feature.repo.domain.model.RunStatus.COMPLETED -> "已完成"
        com.gy.githubclient.feature.repo.domain.model.RunStatus.IN_PROGRESS -> "进行中"
        com.gy.githubclient.feature.repo.domain.model.RunStatus.QUEUED -> "排队中"
        com.gy.githubclient.feature.repo.domain.model.RunStatus.UNKNOWN -> "未知"
    }
