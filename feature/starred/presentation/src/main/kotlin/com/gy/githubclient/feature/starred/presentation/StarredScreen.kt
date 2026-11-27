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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.core.presentation.theme.FrostCard
import com.gy.githubclient.core.presentation.theme.FrostDimens
import com.gy.githubclient.core.presentation.theme.LocalFrostColors
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
    val frost = LocalFrostColors.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is StarredEvent.ShowMessage -> snackbarHostState.showSnackbar(event.message.asText())
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(FrostDimens.PagePadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
            Text(stringResource(R.string.title), style = MaterialTheme.typography.headlineSmall, color = frost.textPrimary)
        }
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StarredSort.entries.forEach { sort ->
                FilterChip(
                    selected = uiState.filter.sort == sort,
                    onClick = { viewModel.onSortSelected(sort) },
                    label = {
                        Text(
                            stringResource(
                                if (sort == StarredSort.UPDATED) {
                                    R.string.sort_updated
                                } else {
                                    R.string.sort_created
                                },
                            ),
                        )
                    },
                    // 排序是"单选"语义：朗读时必须能听出当前选中的是哪一项。
                    modifier =
                        Modifier.semantics {
                            contentDescription =
                                stringResource(
                                    if (sort == StarredSort.UPDATED) {
                                        R.string.sort_updated_desc
                                    } else {
                                        R.string.sort_created_desc
                                    },
                                )
                        },
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
                            Text(stringResource(R.string.load_more))
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
            Text(
                stringResource(if (uiState.isRefreshing) R.string.refreshing else R.string.refresh),
            )
        }
    }
}

/**
 * 消息 → 文案（界面层负责"怎么说"）。
 */
@Composable
private fun StarredMessage.asText(): String =
    when (this) {
        is StarredMessage.StarRemoved -> stringResource(R.string.star_removed, fullName)

        is StarredMessage.Error ->
            when (kind) {
                StarredErrorKind.UNAUTHORIZED -> stringResource(R.string.error_unauthorized)
                StarredErrorKind.RATE_LIMITED -> stringResource(R.string.error_rate_limited)
                StarredErrorKind.NOT_FOUND -> stringResource(R.string.error_not_found)
                StarredErrorKind.NETWORK -> stringResource(R.string.error_network, detail.orEmpty())
                StarredErrorKind.HTTP -> stringResource(R.string.error_http, code ?: 0)
                StarredErrorKind.UNKNOWN -> stringResource(R.string.error_unknown, detail.orEmpty())
            }
    }

@Composable
private fun StarredCard(
    repo: Repository,
    onClick: () -> Unit,
    onRemoveStar: () -> Unit,
) {
    val frost = LocalFrostColors.current
    val removeStarDescription = stringResource(R.string.remove_star_desc, repo.fullName)
    FrostCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = repo.fullName,
                style = MaterialTheme.typography.titleMedium,
                color = frost.textPrimary,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onRemoveStar,
                // 移除星标是破坏性操作，读屏时要说明"移除哪个仓库的星标"。
                modifier =
                    Modifier.semantics {
                        contentDescription = removeStarDescription
                    },
            ) { Text(stringResource(R.string.remove_star)) }
        }
        repo.description?.let { desc ->
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = frost.textTertiary,
            )
        }
        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repo.language?.let { Text("● $it", style = MaterialTheme.typography.labelSmall, color = frost.textSecondary) }
            Text("★ ${repo.stargazersCount}", style = MaterialTheme.typography.labelSmall, color = frost.textSecondary)
            Text("⑂ ${repo.forksCount}", style = MaterialTheme.typography.labelSmall, color = frost.textSecondary)
        }
    }
}
