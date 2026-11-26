/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.BuildConfig
import com.gy.githubclient.core.presentation.theme.FrostCard
import com.gy.githubclient.core.presentation.theme.FrostDimens
import com.gy.githubclient.core.presentation.theme.FrostLevel
import com.gy.githubclient.core.presentation.theme.LocalFrostColors
import com.gy.githubclient.core.presentation.theme.LocalFrostContentInsets
import com.gy.githubclient.core.presentation.theme.ThemeMode
import com.gy.githubclient.feature.repo.domain.translation.ClearTranslationCacheUseCase
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * 外观与缓存设置（v0.16.0，v0.19.0 换 Frost 外观）：
 * - 主题三态（跟随系统/浅色/深色）即时生效并持久化；
 * - 代码字号影响 README 代码块与文件浏览；
 * - 译文缓存可手动清除（翻译走第三方免费服务，缓存仅在本机）。
 *
 * v0.19.0：标题与返回按钮交给外壳的 Frost 顶部通栏，页面本身只排内容；
 * 滚动时内容从通栏下方穿过，通栏的实时模糊有真实内容可折射。
 *
 * @param onBack 返回上一页；由外壳通栏的返回按钮承担，保留参数以稳定调用契约。
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    store: AppSettingsStore = koinInject(),
    clearTranslationCache: ClearTranslationCacheUseCase = koinInject(),
) {
    val settings by store.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val frost = LocalFrostColors.current
    val insets = LocalFrostContentInsets.current

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    start = FrostDimens.PagePadding,
                    end = FrostDimens.PagePadding,
                    top = insets.top,
                    bottom = insets.bottom,
                ),
        verticalArrangement = Arrangement.spacedBy(FrostDimens.CardGap),
    ) {
        FrostCard {
            SectionTitle("主题")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    SettingsChip(
                        selected = settings.themeMode == mode,
                        label = mode.label,
                        onClick = { store.setThemeMode(mode) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "深色模式同样适用于 README、代码块与 Diff。",
                style = MaterialTheme.typography.labelSmall,
                color = frost.textSecondary,
            )
        }

        FrostCard {
            SectionTitle("代码字号")
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSettingsStore.CODE_FONT_SIZES.forEach { size ->
                    SettingsChip(
                        selected = settings.codeFontSizeSp == size,
                        label = "${size}sp",
                        onClick = { store.setCodeFontSize(size) },
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(
                text = "fun main() { println(\"Hello, README\") }",
                style =
                    MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = settings.codeFontSizeSp.sp,
                    ),
                color = frost.textPrimary,
            )
        }

        FrostCard {
            SectionTitle("缓存")
            Spacer(Modifier.height(4.dp))
            Text(
                text = "README 译文缓存仅保存在本机，清除后下次翻译会重新请求第三方免费服务。",
                style = MaterialTheme.typography.bodySmall,
                color = frost.textSecondary,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = {
                    scope.launch {
                        runCatching { clearTranslationCache() }
                            .onSuccess { Toast.makeText(context, "译文缓存已清除", Toast.LENGTH_SHORT).show() }
                            .onFailure {
                                Toast.makeText(context, "清除失败：${it.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                            }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = frost.primaryInk),
            ) {
                Text("清除译文缓存")
            }
        }

        Text(
            text = "GitHub Client ${BuildConfig.VERSION_NAME}（${BuildConfig.VERSION_CODE}）· 本地设置，不上传服务器",
            style = MaterialTheme.typography.labelSmall,
            color = frost.textTertiary,
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        color = LocalFrostColors.current.textPrimary,
    )
}

/** 设置项胶囊：静态玻璃填充 + 选中主绿，不使用任何模糊。 */
@Composable
private fun SettingsChip(
    selected: Boolean,
    label: String,
    onClick: () -> Unit,
) {
    val frost = LocalFrostColors.current
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label, style = MaterialTheme.typography.labelMedium) },
        colors =
            FilterChipDefaults.filterChipColors(
                containerColor = frost.fill(FrostLevel.L2),
                labelColor = frost.textSecondary,
                selectedContainerColor = frost.primaryFill.copy(alpha = 0.18f),
                selectedLabelColor = frost.primaryInk,
            ),
    )
}

private val ThemeMode.label: String
    get() =
        when (this) {
            ThemeMode.SYSTEM -> "跟随系统"
            ThemeMode.LIGHT -> "浅色"
            ThemeMode.DARK -> "深色"
        }
