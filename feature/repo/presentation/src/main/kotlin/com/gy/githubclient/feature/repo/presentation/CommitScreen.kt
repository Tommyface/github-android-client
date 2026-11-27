/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.core.presentation.theme.FrostCard
import com.gy.githubclient.core.presentation.theme.LocalFrostColors
import com.gy.githubclient.feature.repo.domain.model.CommitDetail
import com.gy.githubclient.feature.repo.domain.model.DiffLineKind
import com.gy.githubclient.feature.repo.domain.model.parseDiffPatch
import org.koin.androidx.compose.koinViewModel

/**
 * 提交历史页（v0.12.0 N10）：列表 + 打开单次提交详情（变更文件 + Diff）。
 * 顶层根据 detail 是否存在切换列表/详情视图，与文件浏览器的模式一致。
 */
@Composable
fun CommitScreen(
    owner: String,
    name: String,
    onBack: () -> Unit = {},
    viewModel: CommitViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(owner, name) { viewModel.init(owner, name) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = if (uiState.detail != null) viewModel::closeDetail else onBack) {
                Text(if (uiState.detail != null) "← 返回提交列表" else "← 返回")
            }
            Text(
                if (uiState.detail != null) "提交详情" else "$owner/$name · 提交历史",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
        }

        uiState.error?.let { error ->
            FrostCard {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = viewModel::dismissError) { Text("关闭") }
                }
            }
        }

        val detail = uiState.detail
        if (detail != null) {
            CommitDetailView(detail)
        } else {
            CommitListView(uiState, viewModel)
        }
    }
}

@Composable
private fun CommitListView(
    uiState: CommitUiState,
    viewModel: CommitViewModel,
) {
    when {
        uiState.isLoading && uiState.commits.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }

        uiState.commits.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无提交记录", color = LocalFrostColors.current.textTertiary)
            }
        }

        else -> {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.commits, key = { it.sha }) { commit ->
                    FrostCard(onClick = { viewModel.openDetail(commit.sha) }) {
                        Column(
                            Modifier
                                .padding(12.dp)
                                .semantics { contentDescription = "查看提交 ${commit.shortSha} 的详情" },
                        ) {
                            Text(commit.title, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
                            Spacer(Modifier.height(4.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Text(
                                    commit.shortSha,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = LocalFrostColors.current.primaryInk,
                                )
                                commit.authorName?.let { Text(it, style = MaterialTheme.typography.labelSmall) }
                            }
                            commit.authorDate?.let {
                                Text(it, style = MaterialTheme.typography.labelSmall, color = LocalFrostColors.current.textTertiary)
                            }
                        }
                    }
                }
                item {
                    when {
                        uiState.isLoadingMore -> CircularProgressIndicator()
                        uiState.hasMore -> TextButton(onClick = viewModel::loadNextPage) { Text("加载更多") }
                        else ->
                            Text(
                                "已加载全部",
                                style = MaterialTheme.typography.labelSmall,
                                color = LocalFrostColors.current.textTertiary,
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommitDetailView(detail: CommitDetail) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
        item {
            Column {
                Text(detail.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "${detail.shortSha} · ${detail.authorName.orEmpty()} · ${detail.authorDate.orEmpty()}",
                    style = MaterialTheme.typography.labelSmall,
                    color = LocalFrostColors.current.textTertiary,
                )
                Text(
                    "+${detail.totalAdditions} / -${detail.totalDeletions} · ${detail.files.size} 个文件变更",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
        if (detail.files.isEmpty()) {
            item { Text("该提交无文件变更信息。", style = MaterialTheme.typography.bodySmall) }
        }
        items(detail.files, key = { it.filename }) { file ->
            FrostCard {
                Column(Modifier.padding(12.dp)) {
                    Text(file.filename, style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "${file.status} · +${file.additions} / -${file.deletions}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LocalFrostColors.current.textTertiary,
                    )
                    val patch = file.patch
                    if (patch == null) {
                        Text("二进制文件，无文本 diff。", style = MaterialTheme.typography.labelSmall)
                    } else {
                        Spacer(Modifier.height(6.dp))
                        parseDiffPatch(patch).forEach { line ->
                            val color =
                                when (line.kind) {
                                    DiffLineKind.ADD -> Color(0xFF2E7D32)
                                    DiffLineKind.DELETE -> Color(0xFFC62828)
                                    DiffLineKind.HEADER -> Color(0xFF7B5CD6)
                                    DiffLineKind.CONTEXT -> LocalFrostColors.current.textTertiary
                                }
                            Text(
                                line.text,
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace, color = color),
                            )
                        }
                    }
                }
            }
        }
    }
}
