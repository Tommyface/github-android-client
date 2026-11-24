/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.presentation

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.feature.profile.domain.model.OrgMember
import com.gy.githubclient.feature.profile.domain.model.OrgSummary
import org.koin.androidx.compose.koinViewModel

/** 组织信息页（v0.15.0 A5，只读）。 */
@Composable
fun OrgScreen(
    onBack: () -> Unit,
    viewModel: OrgViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text("组织信息", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = uiState.query,
            onValueChange = viewModel::onQueryChange,
            label = { Text("组织名称（如 github）") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = viewModel::load, modifier = Modifier.fillMaxWidth()) {
            Text("查询")
        }
        Spacer(Modifier.height(12.dp))

        if (uiState.isLoading) {
            CircularProgressIndicator()
        }
        uiState.message?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            uiState.org?.let { org ->
                item { OrgSummaryCard(org = org) }
                item {
                    Text(
                        "成员（${uiState.members.size}）",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                    )
                }
                items(uiState.members, key = { it.login }) { member ->
                    OrgMemberRow(member = member)
                }
                item {
                    Text(
                        "数据来源：GitHub REST /orgs/{org} 与 /orgs/{org}/members（只读）。成员列表可能因隐私设置不完整。",
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
private fun OrgSummaryCard(org: OrgSummary) {
    val context = LocalContext.current
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(org.name ?: org.login, style = MaterialTheme.typography.titleMedium)
            Text("@${org.login}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            org.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp)) }
            Spacer(Modifier.height(8.dp))
            Text("公开仓库 ${org.publicRepos} · 关注者 ${org.followers}")
            val meta = listOfNotNull(org.location, org.blog).joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(org.htmlUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("在浏览器查看组织主页")
            }
        }
    }
}

@Composable
private fun OrgMemberRow(member: OrgMember) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("@${member.login}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(12.dp))
    }
}
