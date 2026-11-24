/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.feature.profile.domain.model.GistFile
import com.gy.githubclient.feature.profile.domain.model.GistItem
import org.koin.androidx.compose.koinViewModel

/** Gist 浏览页（v0.15.0 A6，只读）。 */
@Composable
fun GistScreen(
    onBack: () -> Unit,
    viewModel: GistViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val detail = uiState.detail

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = if (detail != null) viewModel::closeDetail else onBack) {
                Text(if (detail != null) "← 返回列表" else "← 返回")
            }
            Text("我的 Gist", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))
        if (uiState.isLoading) {
            CircularProgressIndicator()
        }
        uiState.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (detail != null) {
            GistDetailView(gist = detail)
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.gists, key = { it.id }) { gist ->
                    GistRow(gist = gist, onClick = { viewModel.openDetail(gist.id) })
                }
                item {
                    Text(
                        "数据来源：GitHub REST /users/{login}/gists（只读）。文件正文在打开单条时按 /gists/{id} 加载。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GistRow(
    gist: GistItem,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clickable(onClick = onClick)) {
        Column(Modifier.padding(12.dp)) {
            Text(
                gist.description?.takeIf { it.isNotBlank() } ?: (gist.primaryFile?.filename ?: "未命名 Gist"),
                style = MaterialTheme.typography.bodyMedium,
            )
            val meta = listOfNotNull(gist.primaryLanguage, "${gist.files.size} 个文件").joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun GistDetailView(gist: GistItem) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            gist.description?.takeIf { it.isNotBlank() } ?: "未命名 Gist",
            style = MaterialTheme.typography.titleMedium,
        )
        Text(
            "@${gist.ownerLogin} · ${if (gist.isPublic) "公开" else "私密"}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        gist.files.forEach { file ->
            GistFileView(file = file)
            Spacer(Modifier.height(8.dp))
        }
        TextButton(
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(gist.htmlUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
        ) {
            Text("在浏览器查看 Gist")
        }
    }
}

@Composable
private fun GistFileView(file: GistFile) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(
                file.filename,
                style = MaterialTheme.typography.titleSmall,
            )
            val meta = listOfNotNull(file.language, file.type, "${file.size} B").joinToString(" · ")
            Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            when {
                file.truncated ->
                    Text(
                        "文件过大，正文已截断；请在浏览器查看完整内容。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                file.hasContent -> Text(file.content.orEmpty(), style = MaterialTheme.typography.bodySmall)
                else -> Text("列表端点未返回正文，请稍后重试或到浏览器查看。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
