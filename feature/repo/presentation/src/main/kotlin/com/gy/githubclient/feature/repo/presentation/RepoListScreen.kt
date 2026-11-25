/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.core.presentation.theme.FrostCard
import com.gy.githubclient.core.presentation.theme.FrostDimens
import com.gy.githubclient.core.presentation.theme.FrostLevel
import com.gy.githubclient.core.presentation.theme.FrostSearchField
import com.gy.githubclient.core.presentation.theme.LocalFrostColors
import com.gy.githubclient.core.presentation.theme.LocalFrostContentInsets
import com.gy.githubclient.feature.repo.domain.model.RepoType
import com.gy.githubclient.feature.repo.domain.model.Repository
import org.koin.androidx.compose.koinViewModel

/**
 * 仓库管理主页面（v0.19.0 Frost）：搜索 + 筛选 + 玻璃卡片列表。
 *
 * 布局：列表铺满整屏，顶部内边距交给 Frost 通栏占位，滚动内容会从通栏下方穿过，
 * 通栏的实时模糊因此有真实内容可折射。
 *
 * 性能约束：列表项一律 L2 静态玻璃（零模糊图层），全屏动态模糊层只有外壳层的 2 个
 * （顶部通栏 + 底部导航），滚动时由外壳统一下发降级信号（半径归零、不透明度提升）。
 */
@Composable
fun RepoListScreen(
    onOpenDetail: (owner: String, name: String) -> Unit,
    viewModel: RepoViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val repos by viewModel.repositories.collectAsStateWithLifecycle()
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
                    bottom = insets.bottom + 64.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(FrostDimens.CardGap),
        ) {
            item(key = "repo-search") {
                FrostSearchField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChange,
                    placeholder = "搜索仓库",
                )
            }

            item(key = "repo-types") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(RepoType.entries.toList(), key = { it.name }) { type ->
                        FilterChip(
                            selected = uiState.filter.type == type,
                            onClick = { viewModel.onTypeSelected(type) },
                            label = { Text(type.label, style = MaterialTheme.typography.labelMedium) },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    containerColor = frost.fill(FrostLevel.L2),
                                    labelColor = frost.textSecondary,
                                    selectedContainerColor = frost.primaryFill.copy(alpha = 0.18f),
                                    selectedLabelColor = frost.primaryInk,
                                ),
                        )
                    }
                }
            }

            when {
                uiState.isRefreshing && repos.isEmpty() -> {
                    item(key = "repo-loading") {
                        LoadingBlock()
                    }
                }

                repos.isEmpty() -> {
                    item(key = "repo-empty") {
                        EmptyBlock(text = "暂无仓库，下拉或点击刷新")
                    }
                }

                else -> {
                    items(repos, key = { it.id }) { repo ->
                        RepoCard(
                            repo = repo,
                            onClick = { onOpenDetail(repo.owner.login, repo.name) },
                            onToggleStar = { viewModel.toggleStar(repo) },
                        )
                    }
                    item(key = "repo-more") {
                        if (uiState.isLoadingMore) {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                CircularProgressIndicator(
                                    color = frost.primaryInk,
                                    modifier = Modifier.height(24.dp),
                                )
                            }
                        } else {
                            TextButton(
                                onClick = viewModel::loadMore,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.textButtonColors(contentColor = frost.primaryInk),
                            ) { Text("加载更多") }
                        }
                    }
                }
            }
        }

        // 悬浮刷新按钮：贴右下角、位于底部导航胶囊之上，列表底部内边距已为它预留空间。
        Button(
            onClick = viewModel::refresh,
            modifier =
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = FrostDimens.PagePadding, bottom = insets.bottom + 4.dp)
                    .height(40.dp),
            enabled = !uiState.isRefreshing,
            shape = RoundedCornerShape(14.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = frost.primaryFill,
                    contentColor = frost.onPrimary,
                ),
        ) {
            Text(if (uiState.isRefreshing) "刷新中…" else "刷新")
        }
    }
}

@Composable
private fun LoadingBlock() {
    val frost = LocalFrostColors.current
    Box(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = frost.primaryInk)
    }
}

@Composable
private fun EmptyBlock(text: String) {
    val frost = LocalFrostColors.current
    Box(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = frost.textSecondary)
    }
}

@Composable
private fun RepoCard(
    repo: Repository,
    onClick: () -> Unit,
    onToggleStar: () -> Unit,
) {
    val frost = LocalFrostColors.current

    FrostCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = repo.fullName,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = frost.textPrimary,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onToggleStar,
                colors =
                    ButtonDefaults.textButtonColors(
                        contentColor = if (repo.isStarred) frost.primaryInk else frost.textTertiary,
                    ),
            ) {
                Text(if (repo.isStarred) "★" else "☆")
            }
        }
        repo.description?.let { desc ->
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = frost.textSecondary,
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repo.language?.let {
                Text(
                    text = "● $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = frost.textSecondary,
                )
            }
            Text("★ ${repo.stargazersCount}", style = MaterialTheme.typography.labelSmall, color = frost.textSecondary)
            Text("⑂ ${repo.forksCount}", style = MaterialTheme.typography.labelSmall, color = frost.textSecondary)
            if (repo.isPrivate) {
                Text(
                    text = "私有",
                    style = MaterialTheme.typography.labelSmall,
                    color = frost.primaryInk,
                )
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
