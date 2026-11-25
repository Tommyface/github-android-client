/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.settings

import android.content.Context
import com.gy.githubclient.core.presentation.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 应用外观设置（v0.16.0）。 */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val codeFontSizeSp: Int = AppSettingsStore.DEFAULT_CODE_FONT_SIZE_SP,
)

/**
 * 外观设置持久化（SharedPreferences）：
 * - 主题与代码字号都是小配置，无需 DataStore 的异步复杂度；
 * - 内存用 StateFlow 暴露，Compose 收集后立即生效。
 */
class AppSettingsStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(load())
    val settings: StateFlow<AppSettings> = _settings.asStateFlow()

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString(KEY_THEME, mode.name).apply()
        _settings.value = _settings.value.copy(themeMode = mode)
    }

    fun setCodeFontSize(sp: Int) {
        val normalized = sp.takeIf { it in CODE_FONT_SIZES } ?: DEFAULT_CODE_FONT_SIZE_SP
        prefs.edit().putInt(KEY_CODE_FONT, normalized).apply()
        _settings.value = _settings.value.copy(codeFontSizeSp = normalized)
    }

    private fun load(): AppSettings {
        val mode =
            prefs
                .getString(KEY_THEME, null)
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM
        val font =
            prefs
                .getInt(KEY_CODE_FONT, DEFAULT_CODE_FONT_SIZE_SP)
                .takeIf { it in CODE_FONT_SIZES }
                ?: DEFAULT_CODE_FONT_SIZE_SP
        return AppSettings(themeMode = mode, codeFontSizeSp = font)
    }

    companion object {
        /** 可选代码字号（sp）：默认 13，与旧版正文等宽字号一致。 */
        val CODE_FONT_SIZES = listOf(12, 13, 15, 17)
        const val DEFAULT_CODE_FONT_SIZE_SP = 13

        private const val PREFS_NAME = "app_settings"
        private const val KEY_THEME = "theme_mode"
        private const val KEY_CODE_FONT = "code_font_size"
    }
}
