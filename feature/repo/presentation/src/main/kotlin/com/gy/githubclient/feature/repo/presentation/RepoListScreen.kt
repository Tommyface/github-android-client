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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.core.presentation.theme.LocalFrostColors
import com.gy.githubclient.core.presentation.theme.FrostCard
import com.gy.githubclient.feature.repo.domain.model.RepoType
import com.gy.githubclient.feature.repo.domain.model.Repository
import org.koin.androidx.compose.koinViewModel

/** 仓库管理主页面：筛选 + 搜索 + 列表 + 刷新/加载更多。 */
@Composable
fun RepoListScreen(
    onOpenDetail: (owner: String, name: String) -> Unit,
    onCreateRepository: () -> Unit = {},
    viewModel: RepoViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val repos by viewModel.repositories.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("我的仓库", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("搜索仓库") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RepoType.entries.forEach { type ->
                FilterChip(
                    selected = uiState.filter.type == type,
                    onClick = { viewModel.onTypeSelected(type) },
                    label = { Text(type.label) },
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
                RepoCard(
                    repo = repo,
                    onClick = { onOpenDetail(repo.owner.login, repo.name) },
                    onToggleStar = { viewModel.toggleStar(repo) },
                )
            }
            item {
                if (repos.isNotEmpty()) {
                    if (uiState.isLoadingMore) {
                        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.height(24.dp))
                        }
                    } else {
                        TextButton(
                            onClick = viewModel::loadMore,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("加载更多") }
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
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onCreateRepository,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.create_repo_entry))
        }
    }
}

@Composable
private fun RepoCard(
    repo: Repository,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
) {
    FrostCard(onClick = onClick) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = repo.fullName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = onToggleStar,
                    // 只念"★"用户不知道在给谁加星标：补上仓库名与动作。
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                if (repo.isStarred) {
                                    "取消 ${repo.fullName} 的星标"
                                } else {
                                    "给 ${repo.fullName} 加星标"
                                }
                        },
                ) {
                    Text(if (repo.isStarred) "★" else "☆")
                }
            }
            repo.description?.let { desc ->
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalFrostColors.current.textTertiary,
                )
            }
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                repo.language?.let { Text("● $it", style = MaterialTheme.typography.labelSmall) }
                Text("★ ${repo.stargazersCount}", style = MaterialTheme.typography.labelSmall)
                Text("⑂ ${repo.forksCount}", style = MaterialTheme.typography.labelSmall)
                if (repo.isPrivate) {
                    Text("私有", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

private val RepoType.label: String
    get() =
        when (this) {
            RepoType.ALL -> "全部"
            RepoType.OWNER -> "我的"
            RepoType.PUBLIC -> "公开"
            RepoType.PRIVATE -> "私有"
            RepoType.MEMBER -> "成员"
        }
