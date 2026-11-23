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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.feature.discover.domain.model.ReleaseAsset
import org.koin.androidx.compose.koinViewModel

/** Release 详情页：展示最新发布的资产列表，支持镜像加速下载与分享。 */
@Composable
fun ReleaseDetailScreen(
    owner: String,
    name: String,
    onBack: () -> Unit = {},
    viewModel: DiscoverViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val item = uiState.allItems.firstOrNull { it.featured.owner == owner && it.featured.name == name }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text("$owner/$name", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(8.dp))

        if (item == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("未找到该应用，请返回列表刷新后重试", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        val release = item.latestRelease
        if (release == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("该仓库暂无 Release 或拉取失败", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        Text("版本 ${release.tagName}", style = MaterialTheme.typography.titleMedium)
        if (release.isPrerelease) {
            Text("预发布版本", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        }
        Spacer(Modifier.height(6.dp))
        release.publishedAt?.let {
            Text("发布于 $it", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        Text("下载资产", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(release.assets, key = { it.id }) { asset ->
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
