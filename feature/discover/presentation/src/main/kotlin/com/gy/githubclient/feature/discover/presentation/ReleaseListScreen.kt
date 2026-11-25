/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.core.presentation.theme.FrostCard
import com.gy.githubclient.core.presentation.theme.FrostDimens
import com.gy.githubclient.core.presentation.theme.FrostTopBar
import com.gy.githubclient.core.presentation.theme.LocalFrostColors
import com.gy.githubclient.core.presentation.theme.LocalFrostContentInsets
import com.gy.githubclient.feature.discover.domain.model.MirrorDownloadLink
import com.gy.githubclient.feature.discover.domain.model.ReleaseAsset
import com.gy.githubclient.feature.discover.domain.model.ReleaseInfo
import com.gy.githubclient.feature.discover.domain.model.TagInfo
import org.koin.androidx.compose.koinViewModel

/**
 * 任意仓库的多版本 Release / Tag 列表页（v0.8.0 M15）。
 * 入口：仓库详情页「📦 Releases」按钮。资产下载复用发现页同一条链
 * （镜像加速 + SHA-256 + 断点续传 + APK 签名校验）。
 *
 * v0.19.0 Frost：单层 LazyColumn 承载筛选与列表，标题交给顶部通栏，
 * 内容可以滚到通栏下方。
 */
@Composable
fun ReleaseListScreen(
    owner: String,
    name: String,
    onBack: () -> Unit = {},
    viewModel: ReleaseListViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(owner, name) {
        viewModel.load(owner, name)
    }

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
                    bottom = insets.bottom + 32.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(FrostDimens.CardGap),
        ) {
            item(key = "release-tabs") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = uiState.tab == ReleaseTab.RELEASES,
                            onClick = { viewModel.selectTab(ReleaseTab.RELEASES) },
                            label = { Text("Releases (${uiState.releases.size})") },
                        )
                        FilterChip(
                            selected = uiState.tab == ReleaseTab.TAGS,
                            onClick = { viewModel.selectTab(ReleaseTab.TAGS) },
                            label = { Text("Tags (${uiState.tags.size})") },
                        )
                        if (uiState.tab == ReleaseTab.RELEASES) {
                            FilterChip(
                                selected = uiState.includePrerelease,
                                onClick = { viewModel.setIncludePrerelease(!uiState.includePrerelease) },
                                label = { Text("含预发布") },
                            )
                        }
                    }
                }
            }

            uiState.error?.let { error ->
                item(key = "release-error") {
                    FrostCard {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                error,
                                style = MaterialTheme.typography.bodySmall,
                                color = frost.danger,
                                modifier = Modifier.weight(1f),
                            )
                            TextButton(onClick = { viewModel.dismissError() }) { Text("关闭") }
                        }
                    }
                }
            }

            if (uiState.isLoading) {
                item(key = "release-loading") {
                    Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }

            when (uiState.tab) {
                ReleaseTab.RELEASES -> {
                    if (!uiState.isLoading && uiState.releases.isEmpty() && uiState.error == null) {
                        item(key = "release-empty") {
                            Text(
                                "该仓库暂无可展示的 Release（草稿与预发布已按当前设置过滤）。",
                                style = MaterialTheme.typography.bodySmall,
                                color = frost.textSecondary,
                            )
                        }
                    }
                    items(uiState.releases, key = { "rel_${it.tagName}" }) { release ->
                        ReleaseCard(
                            release = release,
                            resolveLink = { viewModel.resolveDownload(it) },
                            onDownload = { asset -> viewModel.onDownloadAsset(asset, release.tagName) },
                        )
                    }
                }

                ReleaseTab.TAGS -> {
                    if (!uiState.isLoading && uiState.tags.isEmpty()) {
                        item(key = "tag-empty") {
                            Text(
                                "该仓库暂无 Tag。",
                                style = MaterialTheme.typography.bodySmall,
                                color = frost.textSecondary,
                            )
                        }
                    }
                    items(uiState.tags, key = { "tag_${it.name}" }) { tag -> TagCard(tag) }
                }
            }
        }

        FrostTopBar(
            title = "$owner/$name",
            subtitle = "Releases · Tags",
            onBack = onBack,
            largeTitle = false,
            actions = {
                TextButton(onClick = { viewModel.refresh() }) { Text("刷新") }
            },
        )
    }
}

@Composable
private fun ReleaseCard(
    release: ReleaseInfo,
    resolveLink: (String) -> MirrorDownloadLink,
    onDownload: (ReleaseAsset) -> Unit,
) {
    val frost = LocalFrostColors.current
    FrostCard {
        Text("版本 ${release.tagName}", style = MaterialTheme.typography.titleSmall)
        if (release.isPrerelease) {
            Text("预发布", style = MaterialTheme.typography.labelSmall, color = frost.warning)
        }
        release.publishedAt?.let {
            Text(
                "发布于 ${formatPublishedAt(it)}",
                style = MaterialTheme.typography.labelSmall,
                color = frost.textTertiary,
            )
        }
        if (release.assets.isEmpty()) {
            SpacerHeight()
            Text(
                "该版本无附件资产",
                style = MaterialTheme.typography.bodySmall,
                color = frost.textSecondary,
            )
        } else {
            SpacerHeight()
            Text("资产（${release.assets.size}）", style = MaterialTheme.typography.labelMedium, color = frost.textSecondary)
            release.assets.forEach { asset ->
                AssetRow(asset = asset, link = resolveLink(asset.downloadUrl), onDownload = { onDownload(asset) })
            }
        }
    }
}

@Composable
private fun AssetRow(
    asset: ReleaseAsset,
    link: MirrorDownloadLink,
    onDownload: () -> Unit,
) {
    val frost = LocalFrostColors.current
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(asset.name, style = MaterialTheme.typography.bodySmall, color = frost.textPrimary)
            Text(
                formatAssetSize(asset.size) +
                    if (link.isAccelerated) " · 可加速（${link.mirrorName}）" else " · 直连",
                style = MaterialTheme.typography.labelSmall,
                color = frost.textTertiary,
            )
        }
        Button(onClick = onDownload) { Text("下载") }
    }
}

@Composable
private fun TagCard(tag: TagInfo) {
    val frost = LocalFrostColors.current
    FrostCard {
        Text(tag.name, style = MaterialTheme.typography.titleSmall)
        Text(
            "commit ${tag.commitSha.take(8)}",
            style = MaterialTheme.typography.labelSmall,
            color = frost.textTertiary,
        )
    }
}

@Composable
private fun SpacerHeight() {
    Spacer(Modifier.height(6.dp))
}

private fun formatAssetSize(bytes: Long): String =
    when {
        bytes <= 0 -> "未知大小"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
