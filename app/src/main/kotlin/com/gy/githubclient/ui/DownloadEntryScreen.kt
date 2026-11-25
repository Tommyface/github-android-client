/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gy.githubclient.core.presentation.theme.FrostCard
import com.gy.githubclient.core.presentation.theme.FrostDimens
import com.gy.githubclient.core.presentation.theme.LocalFrostColors

/**
 * 「我的」Tab：提供下载管理与镜像设置入口。
 *
 * v0.19.0 Frost：入口卡片改用 FrostCard（描边高光 + 静态噪点）。该 Tab 不画自绘通栏，
 * 系统栏 / 导航占位由 AppScaffold 统一 padding 提供，页面不再重复消费 LocalFrostContentInsets。
 */
@Composable
fun DownloadEntryScreen(
    onOpenDownloads: () -> Unit,
    onOpenMirrorSettings: () -> Unit,
) {
    val frost = LocalFrostColors.current
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = FrostDimens.PagePadding, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(FrostDimens.CardGap),
    ) {
        Text("我的", style = MaterialTheme.typography.headlineMedium, color = frost.textPrimary)
        Spacer(Modifier.height(4.dp))
        FrostCard {
            Text("下载与加速", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "管理 Release 下载任务，配置镜像代理以加速国内下载。",
                style = MaterialTheme.typography.bodySmall,
                color = frost.textSecondary,
            )
            Spacer(Modifier.height(12.dp))
            Button(onClick = onOpenDownloads, modifier = Modifier.fillMaxWidth()) { Text("下载管理") }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onOpenMirrorSettings, modifier = Modifier.fillMaxWidth()) { Text("镜像设置") }
        }
    }
}
