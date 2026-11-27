/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.presentation.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 玻璃厚度层级：L1 页面级容器 / L2 内容卡片 / L3 浮动导航与浮层。 */
enum class FrostLevel {
    L1,
    L2,
    L3,
}

/**
 * Frost 磨砂玻璃配色令牌（v0.19.0）。
 *
 * 数值与设计基准 `frost-glass-design.html` 对齐：
 * - 浅色玻璃填充 42% / 62% / 55% 白，描边 62% / 75% / 88% 白（竖向渐隐）；
 * - 深色玻璃改用深灰蓝底 + 白色低透明度描边，避免深色下玻璃发灰；
 * - [textPrimary] 与页面底色对比度 ≥ 4.5:1（WCAG AA），[primary] 只用于强调与图标，
 *   正文不使用主绿，保证小字号可读性。
 */
@Immutable
@Suppress("LongParameterList", "TooManyFunctions")
data class FrostColors(
    val isDark: Boolean,
    /** 主绿 #2FA35C：图标、激活态、强调数字。 */
    val primary: Color,
    /** 深绿 #1B7F45：小字号 / 浅底上的可读绿（对比度 ≥ 4.5:1）。 */
    val primaryInk: Color,
    /** 实心绿填充（按钮、选中胶囊）。 */
    val primaryFill: Color,
    val onPrimary: Color,
    val danger: Color,
    val warning: Color,
    val link: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val hairline: Color,
    val pageGradientStart: Color,
    val pageGradientEnd: Color,
    val glowMint: Color,
    val glowBlue: Color,
    val glowIndigo: Color,
    val glowAmber: Color,
    val glassL1: Color,
    val glassL2: Color,
    val glassL3: Color,
    val edgeL1: Color,
    val edgeL2: Color,
    val edgeL3: Color,
    val topBarFill: Color,
    val navBarFill: Color,
    /** 噪点层不透明度（0.04~0.07，仅用于消除玻璃"塑料感"）。 */
    val noiseAlpha: Float,
    /** 状态色：成功（可更新 / 已完成）。 */
    val stateSuccess: Color,
    /** 状态色：警告（可选更新 / 需注意）。 */
    val stateWarning: Color,
    /** 状态色：进行中（检查中 / 下载中）。 */
    val stateInfo: Color,
    /** 状态色：中性（闲置 / 不可比较）。 */
    val stateNeutral: Color,
    /** 状态胶囊激活态的文字色（浅色主题为白，深色主题为深底）。 */
    val onStateFill: Color,
) {
    /** 指定厚度的玻璃填充色。 */
    fun fill(level: FrostLevel): Color =
        when (level) {
            FrostLevel.L1 -> glassL1
            FrostLevel.L2 -> glassL2
            FrostLevel.L3 -> glassL3
        }

    /** 指定厚度的描边发丝线颜色（竖向渐变的高光端）。 */
    fun edge(level: FrostLevel): Color =
        when (level) {
            FrostLevel.L1 -> edgeL1
            FrostLevel.L2 -> edgeL2
            FrostLevel.L3 -> edgeL3
        }
}

/** 浅色 Frost 令牌。 */
val FrostLightColors =
    FrostColors(
        isDark = false,
        primary = Color(0xFF2FA35C),
        primaryInk = Color(0xFF1B7F45),
        primaryFill = Color(0xFF2FA35C),
        onPrimary = Color(0xFFFFFFFF),
        danger = Color(0xFFCF3B3B),
        warning = Color(0xFF9A6A12),
        link = Color(0xFF2C63C8),
        textPrimary = Color(0xFF17202E),
        textSecondary = Color(0xFF556171),
        textTertiary = Color(0xFF7C8798),
        hairline = Color(0x1417202E),
        pageGradientStart = Color(0xFFEFF3F8),
        pageGradientEnd = Color(0xFFE1E7EF),
        glowMint = Color(0xFFC6F0DC),
        glowBlue = Color(0xFFC9DDFB),
        glowIndigo = Color(0xFFDDE6FF),
        glowAmber = Color(0xFFFFE6C9),
        glassL1 = Color(0xFFFFFFFF).copy(alpha = 0.42f),
        glassL2 = Color(0xFFFFFFFF).copy(alpha = 0.62f),
        glassL3 = Color(0xFFFFFFFF).copy(alpha = 0.55f),
        edgeL1 = Color(0xFFFFFFFF).copy(alpha = 0.62f),
        edgeL2 = Color(0xFFFFFFFF).copy(alpha = 0.75f),
        edgeL3 = Color(0xFFFFFFFF).copy(alpha = 0.88f),
        topBarFill = Color(0xFFFFFFFF).copy(alpha = 0.86f),
        navBarFill = Color(0xFFFFFFFF).copy(alpha = 0.84f),
        noiseAlpha = 0.05f,
        stateSuccess = Color(0xFF1F8B4C),
        stateWarning = Color(0xFF9A6A12),
        stateInfo = Color(0xFF2C63C8),
        stateNeutral = Color(0xFF6E7F92),
        onStateFill = Color(0xFFFFFFFF),
    )

/** 深色 Frost 令牌（玻璃用深灰蓝底，避免纯白叠加导致发灰）。 */
val FrostDarkColors =
    FrostColors(
        isDark = true,
        primary = Color(0xFF3FBE6E),
        primaryInk = Color(0xFF5FD389),
        primaryFill = Color(0xFF2FA35C),
        onPrimary = Color(0xFF07160E),
        danger = Color(0xFFFF8C8C),
        warning = Color(0xFFF0C070),
        link = Color(0xFF7FA8FF),
        textPrimary = Color(0xFFE9EFF7),
        textSecondary = Color(0xFFA2AEBF),
        textTertiary = Color(0xFF6E7B8D),
        hairline = Color(0x12FFFFFF),
        pageGradientStart = Color(0xFF11161D),
        pageGradientEnd = Color(0xFF0A0E13),
        glowMint = Color(0xFF1C4F3B),
        glowBlue = Color(0xFF1B3355),
        glowIndigo = Color(0xFF242A52),
        glowAmber = Color(0xFF4A3122),
        glassL1 = Color(0xFF222A36).copy(alpha = 0.42f),
        glassL2 = Color(0xFF202834).copy(alpha = 0.62f),
        glassL3 = Color(0xFF1D2531).copy(alpha = 0.55f),
        edgeL1 = Color(0xFFFFFFFF).copy(alpha = 0.10f),
        edgeL2 = Color(0xFFFFFFFF).copy(alpha = 0.13f),
        edgeL3 = Color(0xFFFFFFFF).copy(alpha = 0.16f),
        topBarFill = Color(0xFF181F2A).copy(alpha = 0.84f),
        navBarFill = Color(0xFF202834).copy(alpha = 0.82f),
        noiseAlpha = 0.07f,
        stateSuccess = Color(0xFF3FB950),
        stateWarning = Color(0xFFD29922),
        stateInfo = Color(0xFF58A6FF),
        stateNeutral = Color(0xFF8B98A9),
        onStateFill = Color(0xFF0B1220),
    )

/** 当前生效的 Frost 令牌；未包裹主题时回落到浅色，保证组件可独立预览。 */
val LocalFrostColors = staticCompositionLocalOf { FrostLightColors }

/** Frost 尺寸与模糊半径令牌（dp 与设计稿 px 按 1:1 折算）。 */
@Suppress("TooManyFunctions")
object FrostDimens {
    val PagePadding: Dp = 16.dp
    val SectionGap: Dp = 12.dp

    /** 顶部通栏内容区高度（不含状态栏）。 */
    val TopBarHeight: Dp = 56.dp

    /** 通栏底部渐变收尾高度：无分割线，与滚动内容无缝衔接。 */
    val TopBarFade: Dp = 18.dp

    val NavBarHeight: Dp = 56.dp
    val NavBarSideGap: Dp = 14.dp
    val NavBarBottomGap: Dp = 12.dp
    val NavBarRadius: Dp = 28.dp
    val NavIconSize: Dp = 18.dp
    val NavIconStroke: Dp = 1.6.dp

    val CardRadius: Dp = 16.dp
    val CardPaddingH: Dp = 16.dp
    val CardPaddingV: Dp = 14.dp
    val CardGap: Dp = 10.dp

    val SearchHeight: Dp = 40.dp
    val SearchRadius: Dp = 12.dp

    val BlurL1: Dp = 28.dp
    val BlurL2: Dp = 20.dp
    val BlurL3: Dp = 32.dp
    val BlurTopBar: Dp = 26.dp

    /** 各厚度对应的模糊半径。 */
    fun blur(level: FrostLevel): Dp =
        when (level) {
            FrostLevel.L1 -> BlurL1
            FrostLevel.L2 -> BlurL2
            FrostLevel.L3 -> BlurL3
        }
}
