/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.presentation

import android.content.Context
import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.feature.discover.domain.model.DiscoverCategory
import com.gy.githubclient.feature.discover.domain.model.DiscoverItem
import com.gy.githubclient.feature.search.domain.model.RepoHit
import org.koin.androidx.compose.koinViewModel

/** 商店发现页：分类筛选 + 精选 Release 卡片列表 + 下拉刷新 + 分享。 */
@Composable
fun DiscoverScreen(
    onOpenRelease: (owner: String, name: String) -> Unit = { _, _ -> },
    viewModel: DiscoverViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    HandleDiscoverEvents(viewModel, context)

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("发现 · 商店", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::refresh, enabled = !uiState.isRefreshing) {
                Text(if (uiState.isRefreshing) "刷新中" else "刷新")
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "精选开源项目 · Release 实时更新 · 支持镜像加速下载",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))

        // v0.10.0 N5：榜单 tab（精选 / 新发布 / 热门）。
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            RankTab.entries.forEach { tab ->
                FilterChip(
                    selected = uiState.rankTab == tab,
                    onClick = { viewModel.onRankTabSelected(tab) },
                    label = { Text(tab.label) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        when (uiState.rankTab) {
            RankTab.NEW, RankTab.HOT -> RankList(uiState, onOpenRelease)
            RankTab.FEATURED -> FeaturedContent(uiState, viewModel, onOpenRelease)
        }
    }
}

@Composable
private fun FeaturedContent(
    uiState: DiscoverUiState,
    viewModel: DiscoverViewModel,
    onOpenRelease: (owner: String, name: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DiscoverCategory.entries.forEach { category ->
                FilterChip(
                    selected = uiState.category == category,
                    onClick = { viewModel.onCategorySelected(category) },
                    label = { Text(category.label) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            uiState.isRefreshing && uiState.items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.items.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("暂无精选内容", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            else -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(uiState.items, key = { it.featured.fullName }) { item ->
                        DiscoverCard(
                            item = item,
                            onOpen = {
                                viewModel.onOpenDetail(item)
                                onOpenRelease(item.featured.owner, item.featured.name)
                            },
                            onShare = { viewModel.onShare(item) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverCard(
    item: DiscoverItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onOpen,
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(item.featured.fullName, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                item.featured.tagline,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.featured.topics.take(3).forEach { topic ->
                    Text(
                        "#$topic",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val release = item.latestRelease
            Text(
                if (release != null) "最新版本 ${release.tagName} · ${release.assets.size} 个资产" else "暂无 Release 信息",
                style = MaterialTheme.typography.labelMedium,
                color = if (release != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                TextButton(onClick = onOpen) { Text("详情 / 下载") }
                TextButton(onClick = onShare) { Text("分享") }
            }
        }
    }
}

/** 收集一次性事件：下载链接交给系统浏览器打开。 */
@Composable
private fun HandleDiscoverEvents(
    viewModel: DiscoverViewModel,
    context: Context,
) {
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is DiscoverEvent.OpenUrl -> {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(event.url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }

                is DiscoverEvent.OpenRelease,
                is DiscoverEvent.Share,
                is DiscoverEvent.ShowMessage,
                -> Unit // 分享已由 ViewModel 直接触发系统分享；导航由卡片回调处理。
            }
        }
    }
}

/** 榜单列表（v0.10.0 N5）：新发布/热门仓库卡片，复用 Search API 结果。 */
@Composable
private fun RankList(
    uiState: DiscoverUiState,
    onOpenRelease: (owner: String, name: String) -> Unit,
) {
    when {
        uiState.isRankLoading && uiState.rankItems.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        }

        uiState.rankItems.isEmpty() -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("暂无榜单数据（Search API 可能限流）", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        else -> {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(uiState.rankItems, key = { it.id }) { hit ->
                    RankCard(hit, onOpenRelease)
                }
            }
        }
    }
}

@Composable
private fun RankCard(
    hit: RepoHit,
    onOpen: (owner: String, name: String) -> Unit,
) {
    val parts = hit.fullName.split("/")
    val owner = parts.getOrNull(0).orEmpty()
    val name = parts.getOrNull(1).orEmpty()
    Card(modifier = Modifier.fillMaxWidth(), onClick = { if (owner.isNotBlank() && name.isNotBlank()) onOpen(owner, name) }) {
        Column(Modifier.padding(14.dp)) {
            Text(hit.fullName, style = MaterialTheme.typography.titleMedium)
            hit.description?.let { desc ->
                Spacer(Modifier.height(4.dp))
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                hit.language?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary) }
                Text("★ ${hit.stargazersCount}", style = MaterialTheme.typography.labelSmall)
                Text("⑂ ${hit.forksCount}", style = MaterialTheme.typography.labelSmall)
            }
            Text(
                "非官方榜单 · 基于 GitHub Search API",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val RankTab.label: String
    get() =
        when (this) {
            RankTab.FEATURED -> "精选"
            RankTab.NEW -> "新发布"
            RankTab.HOT -> "热门"
        }

private val DiscoverCategory.label: String
    get() =
        when (this) {
            DiscoverCategory.ALL -> "全部"
            DiscoverCategory.ANDROID -> "安卓"
            DiscoverCategory.DEVELOPER -> "开发"
            DiscoverCategory.MEDIA -> "影音"
            DiscoverCategory.UTILITY -> "工具"
            DiscoverCategory.GAMES -> "游戏"
        }
