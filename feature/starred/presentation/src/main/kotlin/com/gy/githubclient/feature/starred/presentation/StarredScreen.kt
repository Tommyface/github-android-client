/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.starred.presentation

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.feature.repo.domain.model.Repository
import com.gy.githubclient.feature.starred.domain.model.StarredSort
import org.koin.androidx.compose.koinViewModel

/** 星标仓库页：排序切换 + 列表 + 刷新/加载更多 + 取消星标。 */
@Composable
fun StarredScreen(
    onOpenDetail: (owner: String, name: String) -> Unit,
    onBack: () -> Unit,
    viewModel: StarredViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val repos by viewModel.starredRepos.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StarredEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("返回") }
            Text("⭐ 星标仓库", style = MaterialTheme.typography.headlineSmall)
        }
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StarredSort.entries.forEach { sort ->
                FilterChip(
                    selected = uiState.filter.sort == sort,
                    onClick = { viewModel.onSortSelected(sort) },
                    label = { Text(if (sort == StarredSort.UPDATED) "最近更新" else "最近创建") },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        if (uiState.isRefreshing && repos.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(repos, key = { it.id }) { repo ->
                StarredCard(
                    repo = repo,
                    onClick = { onOpenDetail(repo.owner.login, repo.name) },
                    onRemoveStar = { viewModel.removeStar(repo) },
                )
            }
            item {
                if (repos.isNotEmpty()) {
                    if (uiState.isLoadingMore) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.height(24.dp))
                        }
                    } else if (uiState.hasMore) {
                        TextButton(onClick = viewModel::loadMore, modifier = Modifier.fillMaxWidth()) {
                            Text("加载更多")
                        }
                    }
                }
            }
        }

        Button(
            onClick = viewModel::refresh,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isRefreshing,
        ) {
            Text(if (uiState.isRefreshing) "刷新中…" else "刷新")
        }
    }
}

@Composable
private fun StarredCard(
    repo: Repository,
    onClick: () -> Unit,
    onRemoveStar: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = repo.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRemoveStar) { Text("★ 移除") }
            }
            repo.description?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repo.language?.let { Text("● $it", style = MaterialTheme.typography.labelSmall) }
                Text("★ ${repo.stargazersCount}", style = MaterialTheme.typography.labelSmall)
                Text("⑂ ${repo.forksCount}", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}
