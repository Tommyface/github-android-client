/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.presentation.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** 主题模式（v0.16.0 外观设置）：跟随系统 / 强制浅色 / 强制深色。 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
}

/** 代码等宽字号：由设置页提供，README 代码块与文件浏览共用。 */
val LocalCodeFontSize = staticCompositionLocalOf<TextUnit> { 13.sp }

/**
 * 应用统一主题（v0.19.0 Frost 磨砂玻璃）：
 * - 只使用 Material3 内置编排，不引入动态取色（避免不同 ROM 下对比度漂移）；
 * - 玻璃配色全部经 [LocalFrostColors] 下发，组件不写死色值；
 * - [LocalFrostNoise] 提供一次性生成的噪点贴图，消除玻璃塑料感；
 * - 根节点包一层 [FrostPageBackground]，页面底色与光斑只画一次，滚动不再重绘底图。
 */
@Composable
fun GithubClientTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    codeFontSize: TextUnit = 13.sp,
    content: @Composable () -> Unit,
) {
    val darkTheme =
        when (themeMode) {
            ThemeMode.SYSTEM -> isSystemInDarkTheme()
            ThemeMode.LIGHT -> false
            ThemeMode.DARK -> true
        }
    val frost = if (darkTheme) FrostDarkColors else FrostLightColors
    val noise = rememberFrostNoiseTexture()
    CompositionLocalProvider(
        LocalCodeFontSize provides codeFontSize,
        LocalFrostColors provides frost,
        LocalFrostNoise provides noise,
    ) {
        MaterialTheme(
            colorScheme = frostColorScheme(frost, darkTheme),
            content = {
                FrostPageBackground { content() }
            },
        )
    }
}

/**
 * 由 Frost 令牌派生 Material3 配色：
 * - background 置透明，页面底色交给 [FrostPageBackground]（避免出现纯色遮挡光斑）；
 * - surface 使用玻璃 L2，未改造的旧页面也不会出现"白底挖洞"；
 * - primary 保持 #2FA35C，深色模式用 #3FBE6E 保证暗底可读。
 */
private fun frostColorScheme(
    frost: FrostColors,
    darkTheme: Boolean,
): ColorScheme {
    val scheme =
        if (darkTheme) {
            darkColorScheme(
                primary = frost.primary,
                onPrimary = frost.onPrimary,
                primaryContainer = frost.primaryFill.copy(alpha = 0.22f),
                onPrimaryContainer = frost.primaryInk,
                secondary = frost.primaryInk,
                onSecondary = frost.onPrimary,
                secondaryContainer = frost.primaryFill.copy(alpha = 0.14f),
                onSecondaryContainer = frost.primaryInk,
                tertiary = frost.link,
                onTertiary = Color.White,
                background = Color.Transparent,
                onBackground = frost.textPrimary,
                surface = frost.glassL2,
                onSurface = frost.textPrimary,
                surfaceVariant = frost.glassL3,
                onSurfaceVariant = frost.textSecondary,
                outline = frost.hairline,
                outlineVariant = frost.hairline,
                error = frost.danger,
                onError = Color(0xFF2A0A0A),
                scrim = Color(0xFF000000),
            )
        } else {
            lightColorScheme(
                primary = frost.primary,
                onPrimary = frost.onPrimary,
                primaryContainer = frost.primaryFill.copy(alpha = 0.14f),
                onPrimaryContainer = frost.primaryInk,
                secondary = frost.primaryInk,
                onSecondary = frost.onPrimary,
                secondaryContainer = frost.primaryFill.copy(alpha = 0.10f),
                onSecondaryContainer = frost.primaryInk,
                tertiary = frost.link,
                onTertiary = Color.White,
                background = Color.Transparent,
                onBackground = frost.textPrimary,
                surface = frost.glassL2,
                onSurface = frost.textPrimary,
                surfaceVariant = frost.glassL3,
                onSurfaceVariant = frost.textSecondary,
                outline = frost.hairline,
                outlineVariant = frost.hairline,
                error = frost.danger,
                onError = Color.White,
                scrim = Color(0xFF000000),
            )
        }
    return scheme.withFrostSurfaces(frost)
}

/**
 * 补齐 Material3 容器色阶（surfaceContainer 系列）。
 *
 * 未改造的旧页面仍在使用 Material3 的 Card / Dialog / BottomSheet / Menu，
 * 这些组件默认取 surfaceContainer*。若不覆盖，深色主题下会出现"白卡片压深底"
 * 的硬边块。这里全部回落到 Frost 玻璃令牌，保证单一色源、不写死色值。
 */
private fun ColorScheme.withFrostSurfaces(frost: FrostColors): ColorScheme =
    copy(
        surfaceDim = frost.pageGradientEnd,
        surfaceBright = frost.glassL3,
        surfaceContainerLowest = frost.glassL1,
        surfaceContainerLow = frost.glassL1,
        surfaceContainer = frost.glassL2,
        surfaceContainerHigh = frost.glassL2,
        surfaceContainerHighest = frost.glassL3,
    )
