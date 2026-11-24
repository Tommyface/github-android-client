/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.library.presentation

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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.feature.library.domain.model.AbiVariant
import com.gy.githubclient.feature.library.domain.model.LibraryEntry
import org.koin.androidx.compose.koinViewModel

/**
 * 应用库页（对应参考项目的「应用库」）：追踪一批仓库的 Release，
 * 批量检查更新、按可更新/已是最新分组、单项或一键更新。
 */
@Composable
fun LibraryScreen(
    onOpenRelease: (owner: String, name: String) -> Unit = { _, _ -> },
    viewModel: LibraryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("应用库", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            TextButton(onClick = { showAdd = !showAdd }) {
                Text(if (showAdd) "收起" else "＋ 添加")
            }
        }
        Text(
            "追踪仓库 Release · 批量检查更新 · 安全下载校验",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))

        if (showAdd) {
            AddTrackedForm(onAdd = { owner, name, display, installed, abi ->
                viewModel.add(owner, name, display, installed, abi)
                showAdd = false
            })
            Spacer(Modifier.height(8.dp))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = viewModel::checkAll,
                enabled = !uiState.isChecking && uiState.entries.isNotEmpty(),
                modifier = Modifier.weight(1f),
            ) {
                Text(if (uiState.isChecking) "正在检查更新…" else "检查更新")
            }
            OutlinedButton(
                onClick = viewModel::updateAll,
                enabled = uiState.entries.any { it.hasUpdate },
                modifier = Modifier.weight(1f),
            ) {
                Text("全部更新")
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            uiState.entries.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("还没有追踪的应用，点「＋ 添加」开始", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            else -> {
                val updatable = uiState.entries.filter { it.hasUpdate }
                val upToDate = uiState.entries.filter { !it.hasUpdate }
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (updatable.isNotEmpty()) {
                        item { SectionTitle("${updatable.size} 个可用更新 · 逐个审查或一键全部更新") }
                        items(updatable, key = { it.app.fullName }) { entry ->
                            EntryCard(entry, onOpenRelease, viewModel::update, viewModel::remove)
                        }
                    }
                    if (upToDate.isNotEmpty()) {
                        item { SectionTitle("已是最新 · ${upToDate.size}") }
                        items(upToDate, key = { it.app.fullName }) { entry ->
                            EntryCard(entry, onOpenRelease, onUpdate = null, onRemove = viewModel::remove)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun AddTrackedForm(onAdd: (String, String, String, String, String) -> Unit) {
    var owner by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var display by remember { mutableStateOf("") }
    var installed by remember { mutableStateOf("") }
    var abi by remember { mutableStateOf(AbiVariant.ANY) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(value = owner, onValueChange = {
                owner = it
            }, label = { Text("Owner（如 aiooby）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = name, onValueChange = {
                name = it
            }, label = { Text("仓库名（如 zhenguojian-tv）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = display, onValueChange = {
                display = it
            }, label = { Text("显示名（可选）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = installed, onValueChange = {
                installed = it
            }, label = { Text("已安装版本（如 v0.8.31，可留空）") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(8.dp))
            Text("变体", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AbiVariant.entries.forEach { variant ->
                    androidx.compose.material3.FilterChip(
                        selected = abi == variant,
                        onClick = { abi = variant },
                        label = { Text(variant.label) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onAdd(owner, name, display, installed, abi.token) },
                enabled = owner.isNotBlank() && name.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("添加追踪")
            }
        }
    }
}

@Composable
private fun EntryCard(
    entry: LibraryEntry,
    onOpenRelease: (String, String) -> Unit,
    onUpdate: ((LibraryEntry) -> Unit)?,
    onRemove: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(entry.app.displayName, style = MaterialTheme.typography.titleMedium)
            Text(entry.app.fullName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(4.dp))
            when {
                entry.error != null ->
                    Text(
                        "检查失败：${entry.error}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                entry.hasUpdate -> {
                    Text(
                        "${entry.app.installedVersion.ifBlank { "未安装" }} → ${entry.latestVersion.orEmpty()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    entry.download?.let { Text("变体：${it.assetName}", style = MaterialTheme.typography.labelSmall) }
                    entry.publishedAt?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else ->
                    Text(
                        "${entry.latestVersion.orEmpty()} ${entry.publishedAt.orEmpty()} · 已是最新",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (onUpdate != null && entry.hasUpdate) {
                    Button(onClick = { onUpdate(entry) }) { Text("更新") }
                }
                TextButton(onClick = { onOpenRelease(entry.app.owner, entry.app.name) }) { Text("详情") }
                TextButton(onClick = { onRemove(entry.app.fullName) }) { Text("移除") }
            }
        }
    }
}
