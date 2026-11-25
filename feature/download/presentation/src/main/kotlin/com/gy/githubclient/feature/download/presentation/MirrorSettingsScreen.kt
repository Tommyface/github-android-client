/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.presentation

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
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
import com.gy.githubclient.core.presentation.theme.FrostCard
import com.gy.githubclient.core.presentation.theme.FrostDimens
import com.gy.githubclient.core.presentation.theme.FrostTopBar
import com.gy.githubclient.core.presentation.theme.LocalFrostColors
import com.gy.githubclient.core.presentation.theme.LocalFrostContentInsets
import org.koin.androidx.compose.koinViewModel

/**
 * 镜像设置页：直连 / 内置镜像 / 自定义模板 + 测速。
 *
 * v0.19.0 Frost：标题交给顶部通栏，正文全部落在玻璃卡片里；
 * 列表铺满整屏、内边距由 LocalFrostContentInsets 下发，内容可以滚到通栏下方。
 */
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
            item(key = "mirror-intro") {
                FrostCard {
                    Text(
                        "镜像代理会经过第三方服务器，可能泄露下载行为；默认直连，请按需选择。",
                        style = MaterialTheme.typography.labelSmall,
                        color = frost.warning,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = viewModel::onMeasure,
                        enabled = !uiState.isMeasuring,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (uiState.isMeasuring) "测速中…" else "测速并选择最快镜像")
                    }
                    uiState.message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = frost.textSecondary,
                        )
                    }
                }
            }

            item(key = "mirror-direct") {
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
            items(sortedMirrors, key = { "mirror_${it.id}" }) { mirror ->
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
            item(key = "mirror-custom") {
                FrostCard {
                    Text("自定义镜像模板", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "支持 {url} 整链或 {owner}/{repo}/{ref}/{path} 分解模板",
                        style = MaterialTheme.typography.labelSmall,
                        color = frost.textSecondary,
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

        FrostTopBar(
            title = "镜像加速设置",
            subtitle = "直连 · 内置镜像 · 自定义模板",
            onBack = onBack,
            largeTitle = false,
        )
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
    val frost = LocalFrostColors.current
    FrostCard(onClick = onSelect, contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier.padding(horizontal = FrostDimens.CardPaddingH, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Column(Modifier.weight(1f)) {
                Text(
                    if (isFastest) "🏆 $name" else name,
                    style = MaterialTheme.typography.titleSmall,
                    color = frost.textPrimary,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = frost.textSecondary,
                )
            }
            when {
                latency != null ->
                    Text(
                        "$latency ms",
                        style = MaterialTheme.typography.labelMedium,
                        color = frost.primaryInk,
                    )

                // 测速过但缺席 = 探测失败（超时/不可达），与「尚未测速」区分展示。
                hasMeasured ->
                    Text(
                        "超时",
                        style = MaterialTheme.typography.labelMedium,
                        color = frost.danger,
                    )

                else ->
                    Text(
                        "未测速",
                        style = MaterialTheme.typography.labelMedium,
                        color = frost.textTertiary,
                    )
            }
        }
    }
}
