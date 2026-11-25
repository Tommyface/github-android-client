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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.core.presentation.theme.FrostCard
import com.gy.githubclient.core.presentation.theme.FrostDimens
import com.gy.githubclient.core.presentation.theme.FrostLevel
import com.gy.githubclient.core.presentation.theme.LocalFrostColors
import com.gy.githubclient.core.presentation.theme.LocalFrostContentInsets
import com.gy.githubclient.feature.discover.domain.model.DiscoverCategory
import com.gy.githubclient.feature.discover.domain.model.DiscoverItem
import com.gy.githubclient.feature.discover.domain.model.PlatformInference
import com.gy.githubclient.feature.discover.domain.model.RankQueryFactory
import com.gy.githubclient.feature.search.domain.model.RepoHit
import org.koin.androidx.compose.koinViewModel

/**
 * 发现页（v0.19.0 Frost）：精选 Release / 趋势 / 新发布 / 热门四个榜单的玻璃卡片流。
 *
 * 性能约束：所有卡片使用 L2 静态玻璃（零模糊图层），页面不新增动态模糊；
 * 列表铺满整屏，顶部占位交给 Frost 通栏，滚动时内容从通栏下方穿过。
 */
@Composable
fun DiscoverScreen(
    onOpenRelease: (owner: String, name: String) -> Unit = { _, _ -> },
    viewModel: DiscoverViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val frost = LocalFrostColors.current
    val insets = LocalFrostContentInsets.current

    HandleDiscoverEvents(viewModel, context)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding =
            PaddingValues(
                start = FrostDimens.PagePadding,
                end = FrostDimens.PagePadding,
                top = insets.top,
                bottom = insets.bottom,
            ),
        verticalArrangement = Arrangement.spacedBy(FrostDimens.CardGap),
    ) {
        item(key = "discover-subtitle") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "精选开源项目 · Release 实时更新 · 支持镜像加速下载",
                    style = MaterialTheme.typography.labelSmall,
                    color = frost.textSecondary,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::refresh, enabled = !uiState.isRefreshing) {
                    Text(
                        text = if (uiState.isRefreshing) "刷新中" else "刷新",
                        color = frost.primaryInk,
                    )
                }
            }
        }

        item(key = "discover-rank-tabs") {
            RankTabRow(selected = uiState.rankTab, onSelect = viewModel::onRankTabSelected)
        }

        when (uiState.rankTab) {
            RankTab.TRENDING, RankTab.NEW, RankTab.HOT -> rankBlock(uiState, viewModel, onOpenRelease)
            RankTab.FEATURED -> featuredBlock(uiState, viewModel, onOpenRelease)
        }
    }
}

/** 精选 tab：分类筛选 + 精选卡片。 */
private fun LazyListScope.featuredBlock(
    uiState: DiscoverUiState,
    viewModel: DiscoverViewModel,
    onOpenRelease: (owner: String, name: String) -> Unit,
) {
    item(key = "discover-categories") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(DiscoverCategory.entries.toList(), key = { it.name }) { category ->
                FrostChip(
                    selected = uiState.category == category,
                    label = category.label,
                    onClick = { viewModel.onCategorySelected(category) },
                )
            }
        }
    }

    when {
        uiState.isRefreshing && uiState.items.isEmpty() -> item(key = "discover-loading") { LoadingBlock() }
        uiState.items.isEmpty() -> item(key = "discover-empty") { NoticeBlock("暂无精选内容") }
        else ->
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

/** 榜单 tab（v0.11.0 N6）：语言筛选 + 趋势 / 新发布 / 热门仓库。 */
private fun LazyListScope.rankBlock(
    uiState: DiscoverUiState,
    viewModel: DiscoverViewModel,
    onOpenRelease: (owner: String, name: String) -> Unit,
) {
    item(key = "discover-languages") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(RankQueryFactory.LANGUAGES, key = { it }) { language ->
                FrostChip(
                    selected = uiState.rankLanguage == language,
                    label = language,
                    onClick = { viewModel.onRankLanguageSelected(language) },
                )
            }
        }
    }

    if (uiState.rankNote.isNotBlank()) {
        item(key = "discover-rank-note") {
            Text(
                text = uiState.rankNote,
                style = MaterialTheme.typography.labelSmall,
                color = LocalFrostColors.current.textSecondary,
            )
        }
    }

    when {
        uiState.isRankLoading && uiState.rankItems.isEmpty() -> item(key = "discover-rank-loading") { LoadingBlock() }
        uiState.rankItems.isEmpty() -> item(key = "discover-rank-empty") {
            NoticeBlock("暂无榜单数据（Search API 可能限流）")
        }

        else ->
            items(uiState.rankItems, key = { it.id }) { hit ->
                RankCard(hit, onOpenRelease)
            }
    }
}

@Composable
private fun RankTabRow(
    selected: RankTab,
    onSelect: (RankTab) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(RankTab.entries.toList(), key = { it.name }) { tab ->
            FrostChip(
                selected = selected == tab,
                label = tab.label,
                onClick = { onSelect(tab) },
            )
        }
    }
}

/** 统一玻璃筛选胶囊：静态填充 + 选中主绿（无模糊）。 */
@Composable
private fun FrostChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val frost = LocalFrostColors.current
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = frost.fill(FrostLevel.L2),
                labelColor = frost.textSecondary,
                selectedContainerColor = frost.primaryFill.copy(alpha = 0.18f),
                selectedLabelColor = frost.primaryInk,
            ),
    )
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
private fun NoticeBlock(text: String) {
    val frost = LocalFrostColors.current
    Box(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = frost.textSecondary)
    }
}

@Composable
private fun DiscoverCard(
    item: DiscoverItem,
    onOpen: () -> Unit,
    onShare: () -> Unit,
) {
    val frost = LocalFrostColors.current

    FrostCard(onClick = onOpen) {
        Text(
            text = item.featured.fullName,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = frost.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.featured.tagline,
            style = MaterialTheme.typography.bodySmall,
            color = frost.textSecondary,
        )
        if (item.featured.topics.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item.featured.topics.take(3).forEach { topic ->
                    Text(
                        text = "#$topic",
                        style = MaterialTheme.typography.labelSmall,
                        color = frost.primaryInk,
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        val release = item.latestRelease
        Text(
            text = if (release != null) "最新版本 ${release.tagName} · ${release.assets.size} 个资产" else "暂无 Release 信息",
            style = MaterialTheme.typography.labelMedium,
            color = if (release != null) frost.primaryInk else frost.textTertiary,
        )
        // v0.11.0 N7：仅 Release 资产下载数有公开字段；明示口径，不冒充仓库总下载数。
        release?.let { rel ->
            val total = rel.assets.sumOf { it.downloadCount }
            if (total > 0) {
                Text(
                    text = "Release 资产下载 $total 次（仅官方发布资产，非仓库总下载）",
                    style = MaterialTheme.typography.labelSmall,
                    color = frost.textSecondary,
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row {
            TextButton(
                onClick = onOpen,
                colors = ButtonDefaults.textButtonColors(contentColor = frost.primaryInk),
            ) { Text("详情 / 下载") }
            TextButton(
                onClick = onShare,
                colors = ButtonDefaults.textButtonColors(contentColor = frost.textSecondary),
            ) { Text("分享") }
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

@Composable
private fun RankCard(
    hit: RepoHit,
    onOpen: (owner: String, name: String) -> Unit,
) {
    val frost = LocalFrostColors.current
    val parts = hit.fullName.split("/")
    val owner = parts.getOrNull(0).orEmpty()
    val name = parts.getOrNull(1).orEmpty()

    FrostCard(
        onClick = { if (owner.isNotBlank() && name.isNotBlank()) onOpen(owner, name) },
    ) {
        Text(
            text = hit.fullName,
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            color = frost.textPrimary,
        )
        hit.description?.let { desc ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = frost.textSecondary,
                maxLines = 2,
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            hit.language?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = frost.primaryInk,
                )
            }
            Text("★ ${hit.stargazersCount}", style = MaterialTheme.typography.labelSmall, color = frost.textSecondary)
            Text("⑂ ${hit.forksCount}", style = MaterialTheme.typography.labelSmall, color = frost.textSecondary)
        }
        // v0.11.0 N7：平台标签启发式推断，仅展示并明示「推断」，不声称官方数据。
        val platforms = PlatformInference.infer(hit.topics, emptyList())
        if (platforms.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = "平台（推断）：${platforms.joinToString(" / ")}",
                style = MaterialTheme.typography.labelSmall,
                color = frost.textTertiary,
            )
        }
        Text(
            text = "非官方榜单 · 基于 GitHub Search API · 下载数无公开 API 故不展示",
            style = MaterialTheme.typography.labelSmall,
            color = frost.textTertiary,
        )
    }
}

private val RankTab.label: String
    get() =
        when (this) {
            RankTab.FEATURED -> "精选"
            RankTab.TRENDING -> "趋势"
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
