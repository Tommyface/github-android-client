/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.presentation

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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.gy.githubclient.core.domain.model.mirror.MirrorPreference
import com.gy.githubclient.core.domain.model.mirror.MirrorType
import org.koin.androidx.compose.koinViewModel

/** 镜像设置页：直连 / 内置镜像 / 自定义模板 + 测速。 */
@Composable
fun MirrorSettingsScreen(
    onBack: () -> Unit = {},
    viewModel: MirrorSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val view = uiState.view
    var customTemplate by remember { mutableStateOf("") }
    // 测速后按延迟升序展示；最快者在 UI 上打标记（v0.6.0 M9）。
    val sortedMirrors = remember(view.mirrors, view.latencies) { sortMirrorsByLatency(view.mirrors, view.latencies) }
    val fastestId = sortedMirrors.firstOrNull { view.latencies.containsKey(it.id) }?.id

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text("镜像加速设置", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "镜像代理会经过第三方服务器，可能泄露下载行为；默认直连，请按需选择。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.height(8.dp))
        Button(onClick = viewModel::onMeasure, enabled = !uiState.isMeasuring, modifier = Modifier.fillMaxWidth()) {
            Text(if (uiState.isMeasuring) "测速中…" else "测速并选择最快镜像")
        }
        uiState.message?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                MirrorOption(
                    name = "直连 GitHub（推荐）",
                    subtitle = "不经过任何第三方",
                    selected = view.preference is MirrorPreference.Direct,
                    latency = null,
                    isFastest = false,
                    hasMeasured = view.latencies.isNotEmpty(),
                    onSelect = viewModel::onSelectDirect,
                )
            }
            items(sortedMirrors, key = { it.id }) { mirror ->
                MirrorOption(
                    name = mirror.name,
                    subtitle = if (mirror.type == MirrorType.OFFICIAL) "官方" else "社区镜像",
                    selected = (view.preference as? MirrorPreference.Selected)?.id == mirror.id,
                    latency = view.latencies[mirror.id],
                    isFastest = mirror.id == fastestId,
                    hasMeasured = view.latencies.isNotEmpty(),
                    onSelect = { viewModel.onSelectMirror(mirror.id) },
                )
            }
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text("自定义镜像模板", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "支持 {url} 整链或 {owner}/{repo}/{ref}/{path} 分解模板",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customTemplate,
                            onValueChange = { customTemplate = it },
                            label = { Text("镜像 URL 模板") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Button(
                            onClick = { viewModel.onSetCustom(customTemplate) },
                            enabled = customTemplate.isNotBlank(),
                        ) { Text("使用自定义镜像") }
                    }
                }
            }
        }
    }
}

@Suppress("LongParameterList") // 镜像选项卡片字段语义各不相同，逐一显式传参优于聚合对象
@Composable
private fun MirrorOption(
    name: String,
    subtitle: String,
    selected: Boolean,
    latency: Int?,
    isFastest: Boolean,
    hasMeasured: Boolean,
    onSelect: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth(), onClick = onSelect) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(if (isFastest) "🏆 $name" else name, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when {
                latency != null ->
                    Text("$latency ms", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)

                // 测速过但缺席 = 探测失败（超时/不可达），与「尚未测速」区分展示。
                hasMeasured -> Text("超时", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)

                else -> Text("未测速", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
