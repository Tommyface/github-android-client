/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.presentation

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.core.presentation.theme.FrostCard
import com.gy.githubclient.core.presentation.theme.FrostDimens
import com.gy.githubclient.core.presentation.theme.LocalFrostColors
import com.gy.githubclient.feature.discover.domain.model.DiscoverCategory
import com.gy.githubclient.feature.discover.domain.model.DiscoverItem
import com.gy.githubclient.feature.discover.domain.model.PlatformInference
import com.gy.githubclient.feature.discover.domain.model.RankQueryFactory
import com.gy.githubclient.feature.search.domain.model.RepoHit
import org.koin.androidx.compose.koinViewModel

/**
 * 商店发现页：榜单 tab（精选/趋势/新发布/热门）+ 分类筛选 + 卡片列表 + 分享。
 *
 * M0-18 批次 3 的重构范围：
 * 1. 文案全部迁 `strings.xml`（含原先写在 ViewModel 里的榜单口径中文）；
 * 2. 配色全部走 Frost 令牌，不再出现 `MaterialTheme.colorScheme` / 裸色值；
 * 3. 补齐无障碍：卡片整节点朗读文本、错误与空态 liveRegion；
 * 4. 顺手清掉历史劣质实现（死事件通道、嵌套同义 clickable、刷新失败静默、筛选条溢出）。
 * 不含任何业务能力增减：路由、下载、分享行为与重构前一致。
 */
@Composable
fun DiscoverScreen(
    onOpenRelease: (owner: String, name: String) -> Unit = { _, _ -> },
    viewModel: DiscoverViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val frost = LocalFrostColors.current
    // 先取出可空错误：`uiState` 是委托属性，直接对它做智能转换不成立。
    val refreshError = uiState.refreshError

    Column(modifier = Modifier.fillMaxSize().padding(FrostDimens.PagePadding)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.title),
                style = MaterialTheme.typography.headlineMedium,
                color = frost.textPrimary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = viewModel::refresh, enabled = !uiState.isRefreshing) {
                Text(stringResource(if (uiState.isRefreshing) R.string.refreshing else R.string.refresh))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.subtitle),
            style = MaterialTheme.typography.labelSmall,
            color = frost.textSecondary,
        )
        Spacer(Modifier.height(FrostDimens.SectionGap))

        // 刷新失败曾经完全静默（事件被消费方忽略），现在以可重试的错误行呈现。
        if (refreshError != null) {
            RefreshErrorRow(error = refreshError, onRetry = viewModel::refresh)
            Spacer(Modifier.height(8.dp))
        }

        // 榜单 tab（v0.11.0 N6 加「趋势」）。
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RankTab.entries.forEach { tab ->
                FilterChip(
                    selected = uiState.rankTab == tab,
                    onClick = { viewModel.onRankTabSelected(tab) },
                    label = { Text(tab.labelText()) },
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        when (uiState.rankTab) {
            RankTab.FEATURED ->
                FeaturedContent(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenRelease = onOpenRelease,
                )

            RankTab.TRENDING, RankTab.NEW, RankTab.HOT ->
                RankList(
                    uiState = uiState,
                    viewModel = viewModel,
                    onOpenRelease = onOpenRelease,
                )
        }
    }
}

/** 精选 Tab：分类筛选 + 精选 Release 卡片。 */
@Composable
private fun FeaturedContent(
    uiState: DiscoverUiState,
    viewModel: DiscoverViewModel,
    onOpenRelease: (owner: String, name: String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 6 个分类在窄屏上会溢出被裁切：改为可横向滚动，避免最后两项永远点不到。
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DiscoverCategory.entries.forEach { category ->
                FilterChip(
                    selected = uiState.category == category,
                    onClick = { viewModel.onCategorySelected(category) },
                    label = { Text(category.labelText()) },
                )
            }
        }
        Spacer(Modifier.height(FrostDimens.SectionGap))

        when {
            uiState.isRefreshing && uiState.items.isEmpty() -> LoadingBlock()

            uiState.items.isEmpty() -> EmptyBlock(text = stringResource(R.string.empty_featured))

            else ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(FrostDimens.CardGap)) {
                    items(uiState.items, key = { it.featured.fullName }) { item ->
                        DiscoverCard(
                            item = item,
                            onOpen = { onOpenRelease(item.featured.owner, item.featured.name) },
                            onShare = { viewModel.onShare(item) },
                        )
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
    val frost = LocalFrostColors.current
    val release = item.latestRelease
    val cardDescription =
        stringResource(
            R.string.card_desc_featured,
            item.featured.fullName,
            item.featured.tagline,
        )

    FrostCard(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = cardDescription },
        onClick = onOpen,
    ) {
        Text(
            text = item.featured.fullName,
            style = MaterialTheme.typography.titleMedium,
            color = frost.textPrimary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = item.featured.tagline,
            style = MaterialTheme.typography.bodyMedium,
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
        Text(
            text =
                if (release != null) {
                    stringResource(R.string.latest_release, release.tagName, release.assets.size)
                } else {
                    stringResource(R.string.no_release)
                },
            style = MaterialTheme.typography.labelMedium,
            color = if (release != null) frost.primaryInk else frost.textSecondary,
        )
        // v0.11.0 N7：仅 Release 资产下载数有公开字段；明示口径，不冒充仓库总下载数。
        val totalDownloads = release?.assets?.sumOf { it.downloadCount } ?: 0
        if (totalDownloads > 0) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.asset_downloads, totalDownloads),
                style = MaterialTheme.typography.labelSmall,
                color = frost.textTertiary,
            )
        }
        Spacer(Modifier.height(6.dp))
        // 原先还有「详情 / 下载」按钮：与整卡 onClick 同一动作，嵌套 clickable 会让读屏
        // 在同一节点上听到两个重复动作，故删除；卡片朗读文本已说明「打开 Release 详情」。
        TextButton(onClick = onShare) { Text(stringResource(R.string.share)) }
    }
}

/** 榜单列表（v0.11.0 N6）：语言筛选 + 趋势/新发布/热门仓库卡片，复用 Search API 结果。 */
@Composable
private fun RankList(
    uiState: DiscoverUiState,
    viewModel: DiscoverViewModel,
    onOpenRelease: (owner: String, name: String) -> Unit,
) {
    val frost = LocalFrostColors.current
    Column(modifier = Modifier.fillMaxSize()) {
        // 10 个语言选项在手机宽度下必然溢出：可横向滚动，而不是把后几个裁掉。
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RankQueryFactory.LANGUAGES.forEach { language ->
                FilterChip(
                    selected = uiState.rankLanguage == language,
                    onClick = { viewModel.onRankLanguageSelected(language) },
                    label = { Text(language) },
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        uiState.rankNote?.let { note ->
            Text(
                text = note.asText(),
                style = MaterialTheme.typography.labelSmall,
                color = frost.textSecondary,
            )
            Spacer(Modifier.height(6.dp))
        }
        when {
            uiState.isRankLoading && uiState.rankItems.isEmpty() -> LoadingBlock()

            uiState.rankItems.isEmpty() -> EmptyBlock(text = stringResource(R.string.empty_rank))

            else ->
                LazyColumn(verticalArrangement = Arrangement.spacedBy(FrostDimens.CardGap)) {
                    items(uiState.rankItems, key = { it.id }) { hit ->
                        RankCard(hit = hit, onOpen = onOpenRelease)
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
    val frost = LocalFrostColors.current
    // 原实现用 fullName.split("/") 现拆现用，owner/name 缺失时静默失联；模型本就有 ownerLogin。
    val owner = hit.ownerLogin.ifBlank { hit.fullName.substringBefore("/") }
    val name = hit.fullName.removePrefix("$owner/")
    val canOpen = owner.isNotBlank() && name.isNotBlank() && name != hit.fullName
    val cardDescription =
        stringResource(
            R.string.card_desc_rank,
            hit.fullName,
            hit.stargazersCount,
            hit.forksCount,
        )

    FrostCard(
        modifier = Modifier.semantics(mergeDescendants = true) { contentDescription = cardDescription },
        onClick = if (canOpen) ({ onOpen(owner, name) }) else null,
    ) {
        Text(
            text = hit.fullName,
            style = MaterialTheme.typography.titleMedium,
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
            hit.language?.let { language ->
                Text(
                    text = language,
                    style = MaterialTheme.typography.labelSmall,
                    color = frost.primaryInk,
                )
            }
            Text(
                text = stringResource(R.string.stars, hit.stargazersCount),
                style = MaterialTheme.typography.labelSmall,
                color = frost.textSecondary,
            )
            Text(
                text = stringResource(R.string.forks, hit.forksCount),
                style = MaterialTheme.typography.labelSmall,
                color = frost.textSecondary,
            )
        }
        // v0.11.0 N7：平台标签启发式推断，仅展示并明示「推断」，不声称官方数据。
        // 榜单卡片没有资产文件清单，第二个入参传空表——推断只依据 topics，口径不放大。
        val platforms = PlatformInference.infer(hit.topics, emptyList())
        if (platforms.isNotEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.platform_inferred, platforms.joinToString(" / ")),
                style = MaterialTheme.typography.labelSmall,
                color = frost.warning,
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.rank_disclaimer),
            style = MaterialTheme.typography.labelSmall,
            color = frost.textTertiary,
        )
    }
}

@Composable
private fun LoadingBlock() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun EmptyBlock(text: String) {
    val frost = LocalFrostColors.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = frost.textSecondary,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
    }
}

/** 刷新失败：文案由语义错误类型映射，礼貌级 liveRegion 保证出错时读屏会播报。 */
@Composable
private fun RefreshErrorRow(
    error: DiscoverError,
    onRetry: () -> Unit,
) {
    val frost = LocalFrostColors.current
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Text(
            text = error.asText(),
            style = MaterialTheme.typography.bodyMedium,
            color = frost.danger,
        )
        Spacer(Modifier.height(4.dp))
        TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
    }
}

/** 语义错误类型 → 界面文案（"怎么说"归界面层，ViewModel 不产出字符串）。 */
@Composable
private fun DiscoverError.asText(): String =
    when (this) {
        DiscoverError.Unauthorized -> stringResource(R.string.error_unauthorized)
        DiscoverError.RateLimited -> stringResource(R.string.error_rate_limited)
        DiscoverError.NotFound -> stringResource(R.string.error_not_found)
        is DiscoverError.Network -> stringResource(R.string.error_network, cause)
        is DiscoverError.Http -> stringResource(R.string.error_http, code)
        DiscoverError.Unknown -> stringResource(R.string.error_unknown)
    }

/** 榜单口径 → 文案。 */
@Composable
private fun RankNoteKind.asText(): String =
    stringResource(
        when (this) {
            RankNoteKind.TRENDING -> R.string.rank_note_trending
            RankNoteKind.NEW -> R.string.rank_note_new
            RankNoteKind.HOT -> R.string.rank_note_hot
        },
    )

@Composable
private fun RankTab.labelText(): String =
    stringResource(
        when (this) {
            RankTab.FEATURED -> R.string.tab_featured
            RankTab.TRENDING -> R.string.tab_trending
            RankTab.NEW -> R.string.tab_new
            RankTab.HOT -> R.string.tab_hot
        },
    )

@Composable
private fun DiscoverCategory.labelText(): String =
    stringResource(
        when (this) {
            DiscoverCategory.ALL -> R.string.category_all
            DiscoverCategory.ANDROID -> R.string.category_android
            DiscoverCategory.DEVELOPER -> R.string.category_developer
            DiscoverCategory.MEDIA -> R.string.category_media
            DiscoverCategory.UTILITY -> R.string.category_utility
            DiscoverCategory.GAMES -> R.string.category_games
        },
    )
