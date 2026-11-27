/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.search.presentation

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.core.domain.link.GitHubLink
import com.gy.githubclient.core.domain.link.GitHubLinkTarget
import com.gy.githubclient.core.presentation.theme.FrostCard
import com.gy.githubclient.core.presentation.theme.FrostDimens
import com.gy.githubclient.core.presentation.theme.FrostLevel
import com.gy.githubclient.core.presentation.theme.FrostSearchField
import com.gy.githubclient.core.presentation.theme.LocalFrostColors
import com.gy.githubclient.core.presentation.theme.LocalFrostContentInsets
import com.gy.githubclient.feature.search.domain.model.RepoHit
import com.gy.githubclient.feature.search.domain.model.SearchType
import com.gy.githubclient.feature.search.domain.model.UserHit
import org.koin.androidx.compose.koinViewModel

/**
 * 全局搜索页（v0.19.0 Frost）：类别切换 + 关键字防抖搜索 + 分页结果 + 仓库结果一键收藏。
 * v0.16.0：输入 GitHub 链接时展示「在应用内打开」卡片；仓库结果可点击进入详情。
 *
 * 性能约束：搜索框自身不做 blur（仅 L2 静态填充 + 发丝线），结果卡片全部为静态玻璃；
 * 页面不再新增模糊层，全屏动态模糊层仍由外壳的 2 层承担。
 */
@Composable
fun SearchScreen(
    onOpenRepo: (String, String) -> Unit = { _, _ -> },
    onOpenLink: (GitHubLink) -> Unit = {},
    viewModel: SearchViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    val frost = LocalFrostColors.current
    val insets = LocalFrostContentInsets.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SearchEvent.ShowMessage -> message = event.message
            }
        }
    }

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
        item(key = "search-field") {
            FrostSearchField(
                value = uiState.term,
                onValueChange = viewModel::onTermChange,
                placeholder = "搜索 ${uiState.type.label}",
            )
        }

        uiState.detectedLink?.let { link ->
            item(key = "search-detected-link") {
                DetectedLinkCard(link = link, onOpen = { onOpenLink(link) })
            }
        }

        item(key = "search-types") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                items(SearchType.entries.toList(), key = { it.name }) { type ->
                    FilterChip(
                        selected = uiState.type == type,
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

        message?.let { text ->
            item(key = "search-message") {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodySmall,
                    color = frost.primaryInk,
                )
            }
        }

        searchResults(
            uiState = uiState,
            onOpenRepo = onOpenRepo,
            onStarRepo = viewModel::starRepository,
            onToggleFollow = viewModel::toggleFollow,
            onLoadMore = viewModel::loadMore,
        )
    }
}

@Suppress("CyclomaticComplexMethod")
private fun LazyListScope.searchResults(
    uiState: SearchUiState,
    onOpenRepo: (String, String) -> Unit,
    onStarRepo: (RepoHit) -> Unit,
    onToggleFollow: (UserHit) -> Unit,
    onLoadMore: () -> Unit,
) {
    when {
        uiState.term.isBlank() -> {
            item(key = "search-hint") { NoticeBlock("输入关键字开始搜索") }
        }

        uiState.isSearching && uiState.hits.isEmpty() -> {
            item(key = "search-loading") { LoadingBlock() }
        }

        uiState.hasSearched && uiState.hits.isEmpty() && !uiState.isSearching -> {
            item(key = "search-empty") { NoticeBlock("没有找到结果") }
        }

        else -> {
            item(key = "search-count") {
                Text(
                    text = "共 ${uiState.totalCount} 条结果",
                    style = MaterialTheme.typography.labelMedium,
                    color = LocalFrostColors.current.textSecondary,
                )
            }
            items(uiState.hits, key = { "${uiState.type.name}_${it.id}" }) { hit ->
                val repoHit = hit as? RepoHit
                val userHit = hit as? UserHit
                val starred = repoHit != null && repoHit.fullName in uiState.starredFullNames
                val following = userHit?.let { uiState.followStatus[it.login] }
                HitCard(
                    title = hit.title,
                    subtitle = hit.subtitle(),
                    starAction =
                        repoHit?.let { repo ->
                            HitStarAction(starred = starred) { onStarRepo(repo) }
                        },
                    followAction =
                        userHit?.let { user ->
                            HitFollowAction(following = following) { onToggleFollow(user) }
                        },
                    onClick =
                        repoHit?.let { repo ->
                            { onOpenRepo(repo.ownerLogin, repo.fullName.substringAfter('/')) }
                        },
                )
            }
            item(key = "search-more") {
                val frost = LocalFrostColors.current
                when {
                    uiState.isLoadingMore -> {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(color = frost.primaryInk, modifier = Modifier.height(24.dp))
                        }
                    }

                    uiState.hasNextPage -> {
                        TextButton(
                            onClick = onLoadMore,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.textButtonColors(contentColor = frost.primaryInk),
                        ) { Text("加载更多") }
                    }
                }
            }
        }
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
private fun DetectedLinkCard(
    link: GitHubLink,
    onOpen: () -> Unit,
) {
    val frost = LocalFrostColors.current

    FrostCard {
        Text(
            text = "识别到 GitHub 链接",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = frost.primaryInk,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = link.target.displayName(),
            style = MaterialTheme.typography.titleSmall,
            color = frost.textPrimary,
        )
        Text(
            text = link.sourceUrl,
            style = MaterialTheme.typography.bodySmall,
            color = frost.textSecondary,
            maxLines = 1,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            TextButton(
                onClick = onOpen,
                colors = ButtonDefaults.textButtonColors(contentColor = frost.primaryInk),
            ) { Text(link.target.actionLabel()) }
        }
    }
}

private data class HitStarAction(
    val starred: Boolean,
    val onClick: () -> Unit,
)

private data class HitFollowAction(
    /** null=尚未查询关注状态。 */
    val following: Boolean?,
    val onClick: () -> Unit,
)

@Composable
private fun HitCard(
    title: String,
    subtitle: String,
    starAction: HitStarAction? = null,
    followAction: HitFollowAction? = null,
    onClick: (() -> Unit)? = null,
) {
    val frost = LocalFrostColors.current

    FrostCard(onClick = onClick) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = frost.textPrimary,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = frost.textSecondary,
            maxLines = 2,
        )
        if (starAction != null || followAction != null) {
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                starAction?.let {
                    TextButton(
                        onClick = it.onClick,
                        enabled = !it.starred,
                        colors = ButtonDefaults.textButtonColors(contentColor = frost.primaryInk),
                    ) {
                        Text(if (it.starred) "已收藏" else "★ 收藏")
                    }
                }
                followAction?.let {
                    TextButton(
                        onClick = it.onClick,
                        colors = ButtonDefaults.textButtonColors(contentColor = frost.primaryInk),
                    ) {
                        Text(
                            when (it.following) {
                                true -> "取消关注"
                                false -> "关注"
                                null -> "关注 / 查看状态"
                            },
                        )
                    }
                }
            }
        }
    }
}

private val SearchType.label: String
    get() =
        when (this) {
            SearchType.REPOSITORY -> "仓库"
            SearchType.CODE -> "代码"
            SearchType.ISSUE -> "Issue"
            SearchType.USER -> "用户"
            SearchType.COMMIT -> "提交"
        }

/** 链接目标的中文描述（搜索页链接卡片展示用）。 */
internal fun GitHubLinkTarget.displayName(): String =
    when (this) {
        is GitHubLinkTarget.Repository -> "仓库 $owner/$repo"
        is GitHubLinkTarget.Files -> "文件浏览 $owner/$repo · $ref"
        is GitHubLinkTarget.Releases -> releaseDisplayName(this)
        is GitHubLinkTarget.Commits -> commitsDisplayName(this)
        is GitHubLinkTarget.Actions -> "Actions $owner/$repo"
        GitHubLinkTarget.Notifications -> "通知中心"
        GitHubLinkTarget.Trending -> "Trending（无应用内页面）"
        GitHubLinkTarget.Gists -> "我的 Gist 列表"
        GitHubLinkTarget.Home -> "GitHub 首页（无应用内页面）"
        is GitHubLinkTarget.Download -> "下载 $fileName"
        is GitHubLinkTarget.External -> "外部页面（无应用内页面）"
    }

private fun releaseDisplayName(target: GitHubLinkTarget.Releases): String =
    if (target.tag != null) {
        "Release ${target.owner}/${target.repo} · ${target.tag}"
    } else {
        "Releases ${target.owner}/${target.repo}"
    }

private fun commitsDisplayName(target: GitHubLinkTarget.Commits): String {
    val suffix = target.ref?.let { " · $it" }.orEmpty()
    return "提交历史 ${target.owner}/${target.repo}$suffix"
}

/** 链接卡片主按钮文案：站内页面才进路由，其余明确交给浏览器/下载队列。 */
internal fun GitHubLinkTarget.actionLabel(): String =
    when (this) {
        is GitHubLinkTarget.Download -> "加入下载队列"
        is GitHubLinkTarget.External, GitHubLinkTarget.Trending, GitHubLinkTarget.Home -> "用浏览器打开"
        else -> "在应用内打开"
    }
