/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.search.presentation

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.feature.search.domain.model.SearchType
import org.koin.androidx.compose.koinViewModel

/** 全局搜索页：类别切换 + 关键字防抖搜索 + 分页结果。 */
@Composable
fun SearchScreen(viewModel: SearchViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("全局搜索", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = uiState.term,
            onValueChange = viewModel::onTermChange,
            label = { Text("搜索 ${uiState.type.label}") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SearchType.entries.forEach { type ->
                FilterChip(
                    selected = uiState.type == type,
                    onClick = { viewModel.onTypeSelected(type) },
                    label = { Text(type.label) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))

        when {
            uiState.term.isBlank() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("输入关键字开始搜索", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            uiState.isSearching && uiState.hits.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            uiState.hasSearched && uiState.hits.isEmpty() && !uiState.isSearching -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有找到结果", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            else -> {
                Text(
                    "共 ${uiState.totalCount} 条结果",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(uiState.hits, key = { "${uiState.type.name}_${it.id}" }) { hit ->
                        HitCard(title = hit.title, subtitle = hit.subtitle())
                    }
                    item {
                        when {
                            uiState.isLoadingMore -> {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.height(24.dp))
                                }
                            }

                            uiState.hasNextPage -> {
                                TextButton(
                                    onClick = viewModel::loadMore,
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("加载更多") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HitCard(
    title: String,
    subtitle: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
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
