/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/** 仓库详情页：信息概览 + 分支列表 + Star 操作。 */
@Composable
fun RepoDetailScreen(
    owner: String,
    name: String,
    onBack: () -> Unit,
    viewModel: RepoViewModel = koinViewModel(),
) {
    val repo by viewModel.detail.collectAsStateWithLifecycle()
    val branches by viewModel.branches.collectAsStateWithLifecycle()

    LaunchedEffect(owner, name) {
        viewModel.openDetail(owner, name)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Button(onClick = onBack) { Text("返回") }
        Spacer(Modifier.height(12.dp))

        if (repo == null) {
            Text("加载中…", style = MaterialTheme.typography.bodyMedium)
            return@Column
        }

        val current = repo ?: return@Column
        Text(current.fullName, style = MaterialTheme.typography.headlineSmall)
        current.description?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("★ ${current.stargazersCount}", style = MaterialTheme.typography.labelLarge)
            Text("⑂ ${current.forksCount}", style = MaterialTheme.typography.labelLarge)
            Text("Issues ${current.openIssuesCount}", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(8.dp))
        Text("默认分支：${current.defaultBranch}", style = MaterialTheme.typography.bodySmall)
        current.language?.let { Text("语言：$it", style = MaterialTheme.typography.bodySmall) }
        Spacer(Modifier.height(12.dp))

        Button(onClick = { viewModel.toggleStar(current) }, modifier = Modifier.fillMaxWidth()) {
            Text(if (current.isStarred) "取消 Star" else "Star 这个仓库")
        }
        Spacer(Modifier.height(16.dp))

        Text("分支（${branches.size}）", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(branches, key = { it.name }) { branch ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp)) {
                        Text(branch.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        if (branch.isProtected) {
                            Text("受保护", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
    }
}
