/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.presentation

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.feature.discover.domain.model.ReleaseAsset
import com.gy.githubclient.feature.discover.domain.model.ReleaseInfo
import org.koin.androidx.compose.koinViewModel

/**
 * Release 详情页：展示发布说明与资产列表，支持镜像加速下载与分享。
 * 整页单一 LazyColumn（信息区作为普通 item），避免 Column 嵌套导致的滚动截断。
 */
@Composable
fun ReleaseDetailScreen(
    owner: String,
    name: String,
    onBack: () -> Unit = {},
    viewModel: DiscoverViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val item = uiState.allItems.firstOrNull { it.featured.owner == owner && it.featured.name == name }

    if (item == null) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← 返回") }
                Text("$owner/$name", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("未找到该应用，请返回列表刷新后重试", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val release = item.latestRelease
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "header") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) { Text("← 返回") }
                Text("$owner/$name", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            }
        }

        if (release == null) {
            item(key = "empty_release") {
                Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                    Text("该仓库暂无 Release 或拉取失败", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            return@LazyColumn
        }

        item(key = "release_info") { ReleaseInfoSection(release) }
        item(key = "assets_title") {
            Text("下载资产", style = MaterialTheme.typography.titleSmall)
        }

        if (release.assets.isEmpty()) {
            item(key = "assets_empty") {
                Text(
                    "该版本没有附件资产——可返回发现页选择其他版本，或到 GitHub 网页查看发布说明。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            items(release.assets, key = { "asset_${it.id}" }) { asset ->
                AssetCard(
                    asset = asset,
                    resolveLink = { viewModel.resolveDownload(it) },
                    onDownload = { viewModel.onDownloadAsset(asset, owner, name, release.tagName) },
                    onShare = { viewModel.onShare(item) },
                )
            }
        }
    }
}

@Composable
private fun ReleaseInfoSection(release: ReleaseInfo) {
    Column {
        Text("版本 ${release.tagName}", style = MaterialTheme.typography.titleMedium)
        if (release.isPrerelease) {
            Text("预发布版本", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(6.dp))
        release.publishedAt?.let {
            Text(
                "发布于 ${formatPublishedAt(it)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        release.body?.takeIf { it.isNotBlank() }?.let { body ->
            Spacer(Modifier.height(10.dp))
            Text("发布说明", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                body,
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Default,
            )
        }
    }
}

/** ISO 8601（2023-07-13T11:09:53Z）→「2023-07-13 11:09 UTC」；解析失败原样返回。 */
internal fun formatPublishedAt(iso: String): String {
    val match = Regex("""^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2})""").find(iso) ?: return iso
    val (date, time) = match.destructured
    return "$date $time UTC"
}

@Composable
private fun AssetCard(
    asset: ReleaseAsset,
    resolveLink: (String) -> com.gy.githubclient.feature.discover.domain.model.MirrorDownloadLink,
    onDownload: () -> Unit,
    onShare: () -> Unit,
) {
    val link = resolveLink(asset.downloadUrl)
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(asset.name, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                formatSize(asset.size) + " · 下载 ${asset.downloadCount} 次",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                if (link.isAccelerated) {
                    "可加速（镜像：${link.mirrorName}）· 实际线路由「镜像设置」偏好决定"
                } else {
                    "直连下载"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (link.isAccelerated) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                TextButton(onClick = onDownload) { Text("下载到设备") }
                TextButton(onClick = onShare) { Text("分享") }
            }
        }
    }
}

private fun formatSize(bytes: Long): String =
    when {
        bytes <= 0 -> "未知大小"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }
