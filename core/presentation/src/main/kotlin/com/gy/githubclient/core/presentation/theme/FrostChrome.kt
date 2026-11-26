/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.presentation.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * 页面内容内边距：由 AppScaffold 依据顶部通栏 / 底部导航的实际占位下发，
 * 页面只消费、不自己算状态栏高度，保证四页与其它页面行为一致。
 */
@Immutable
data class FrostInsets(
    val top: Dp = 0.dp,
    val bottom: Dp = 0.dp,
)

val LocalFrostContentInsets = staticCompositionLocalOf { FrostInsets() }

/** 页面级内边距：左右固定 16dp，上下叠加通栏 / 导航占位。 */
@Composable
fun Modifier.frostContentPadding(
    horizontal: Dp = FrostDimens.PagePadding,
    topExtra: Dp = 8.dp,
    bottomExtra: Dp = 8.dp,
): Modifier {
    val insets = LocalFrostContentInsets.current
    return padding(
        start = horizontal,
        end = horizontal,
        top = insets.top + topExtra,
        bottom = insets.bottom + bottomExtra,
    )
}

/** 线性图标集合：项目无图标库，全部用 Canvas 手绘，保证 1.6dp 发丝线质感一致。 */
enum class FrostIcon(
    /** 无障碍朗读文本（TalkBack）。图标本身无文字，必须由语义补足。 */
    val contentDescription: String,
) {
    BACK("返回"),
    CLOSE("关闭"),
    SEARCH("搜索"),
    GRID("仓库"),
    COMPASS("发现"),
    USER("我的"),
}

/** 底部导航项。 */
@Immutable
data class FrostNavItem(
    val label: String,
    val icon: FrostIcon,
)

/**
 * Frost 顶部通栏（v0.19.0）。
 *
 * 视觉约束：
 * - 自 y=0 起绘制，玻璃底色铺满状态栏区域，与状态栏无缝融合（状态栏透明由 Activity 设置）；
 * - 底部用 18dp 渐变淡出收尾，**不画分割线**；
 * - 这是全应用仅有的 2 个动态模糊层之一（另一个是底部导航），滚动时自动降级。
 */
@Suppress("LongParameterList")
@Composable
fun FrostTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    largeTitle: Boolean = true,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val frost = LocalFrostColors.current
    val motion = LocalFrostMotion.current
    val blurActive = motion?.isFastScrolling != true
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val fill = frost.topBarFill
    val noiseBrush = rememberFrostNoiseBrush()
    // 滚动降级时噪点同步减半，配合半径归零，画面不出现"砂面还在磨"的跳变。
    val noiseAlpha = if (blurActive) frost.noiseAlpha else frost.noiseAlpha * 0.5f
    val barBodyHeight = if (subtitle == null) FrostDimens.TopBarHeight else FrostDimens.TopBarHeight + 12.dp

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(statusTop + barBodyHeight)
                    .drawBehind { drawRect(fill) },
        ) {
            // 光衬 + 噪点同层：静态雾面主体，不挂 renderEffect（见 FrostSurface 的实现说明）。
            // API 24–30 与 API 31+ 表现完全一致，滚动时不产生任何额外图层。
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .drawBehind {
                            drawTopBarSheen(frost)
                            noiseBrush?.let { drawRect(brush = it, alpha = noiseAlpha) }
                        },
            )
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(start = if (onBack != null) 8.dp else 18.dp, end = 8.dp, top = statusTop),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                onBack?.let {
                    FrostIconButton(icon = FrostIcon.BACK, onClick = it)
                }
                Column(
                    modifier = Modifier.weight(1f).padding(start = if (onBack != null) 4.dp else 0.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = title,
                        style = frostTitleStyle(largeTitle),
                        color = frost.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = frost.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    content = actions,
                )
            }
        }
        // 渐变收尾：玻璃色 → 透明，18dp，无分割线。
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(FrostDimens.TopBarFade)
                    .drawBehind {
                        drawRect(
                            Brush.verticalGradient(listOf(fill, Color.Transparent)),
                        )
                    },
        )
    }
}

@Composable
private fun frostTitleStyle(largeTitle: Boolean) =
    if (largeTitle) {
        MaterialTheme.typography.headlineSmall.copy(
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = (-0.2).sp,
        )
    } else {
        MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    }

/** 通栏上的圆形图标按钮（返回 / 关闭等），44dp 触控热区。 */
@Composable
fun FrostIconButton(
    icon: FrostIcon,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalFrostColors.current.textPrimary,
    /** 无障碍朗读文本；默认取图标自带的描述，调用方可覆盖为更具体的说法。 */
    contentDescription: String = icon.contentDescription,
) {
    Box(
        modifier =
            modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onClick)
                // 图标本身没有文字，语义必须补上，否则 TalkBack 只会念"按钮"。
                .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        FrostIconCanvas(icon = icon, tint = tint, modifier = Modifier.size(18.dp))
    }
}

/**
 * Frost 底部玻璃导航（浮动胶囊）。
 *
 * 高度 56dp、圆角 28dp、左右留 14dp、底部留 12dp + 导航栏安全区；
 * 是全应用第 2 个动态模糊层，滚动降级时自动关闭。
 */
@Composable
fun FrostNavBar(
    items: List<FrostNavItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val frost = LocalFrostColors.current
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = FrostDimens.NavBarSideGap,
                    end = FrostDimens.NavBarSideGap,
                    bottom = FrostDimens.NavBarBottomGap + navBarInset,
                ),
    ) {
        FrostSurface(
            modifier = Modifier.fillMaxWidth().height(FrostDimens.NavBarHeight),
            level = FrostLevel.L3,
            shape = RoundedCornerShape(FrostDimens.NavBarRadius),
            blur = true,
            elevated = true,
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val tint = if (selected) frost.primaryInk else frost.textSecondary
                    Column(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clickable { onSelect(index) }
                                .semantics {
                                    contentDescription = item.label
                                    this.selected = selected
                                },
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        FrostIconCanvas(
                            icon = item.icon,
                            tint = tint,
                            modifier = Modifier.size(FrostDimens.NavIconSize),
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.label,
                            fontSize = 10.sp,
                            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                            color = tint,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

/**
 * 玻璃搜索框：静态半透明填充 + 发丝描边，**自身不做 blur**
 * （输入态模糊会随键盘弹出逐帧重算，且会突破 2 层模糊预算）。
 */
@Composable
fun FrostSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val frost = LocalFrostColors.current
    val shape = RoundedCornerShape(FrostDimens.SearchRadius)
    val textStyle = MaterialTheme.typography.bodyMedium.copy(color = frost.textPrimary)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .height(FrostDimens.SearchHeight)
                .clip(shape)
                .background(frost.fill(FrostLevel.L2))
                .border(1.dp, frost.edge(FrostLevel.L2), shape)
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FrostIconCanvas(
            icon = FrostIcon.SEARCH,
            tint = frost.textTertiary,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            enabled = enabled,
            singleLine = true,
            textStyle = textStyle,
            cursorBrush = SolidColor(frost.primaryInk),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            style = textStyle.copy(color = frost.textTertiary),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

/** Canvas 手绘线性图标；尺寸由调用方决定，线宽统一 1.6dp。 */
@Composable
fun FrostIconCanvas(
    icon: FrostIcon,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val strokeWidth = FrostDimens.NavIconStroke.toPx()
        val stroke = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        when (icon) {
            FrostIcon.BACK -> {
                val path =
                    Path().apply {
                        moveTo(w * 0.64f, h * 0.18f)
                        lineTo(w * 0.34f, h * 0.50f)
                        lineTo(w * 0.64f, h * 0.82f)
                    }
                drawPath(path, tint, style = stroke)
            }

            FrostIcon.CLOSE -> {
                drawLine(tint, Offset(w * 0.22f, h * 0.22f), Offset(w * 0.78f, h * 0.78f), strokeWidth, StrokeCap.Round)
                drawLine(tint, Offset(w * 0.78f, h * 0.22f), Offset(w * 0.22f, h * 0.78f), strokeWidth, StrokeCap.Round)
            }

            FrostIcon.SEARCH -> {
                drawCircle(
                    color = tint,
                    radius = w * 0.30f,
                    center = Offset(w * 0.42f, h * 0.42f),
                    style = stroke,
                )
                drawLine(
                    tint,
                    Offset(w * 0.64f, h * 0.64f),
                    Offset(w * 0.86f, h * 0.86f),
                    strokeWidth,
                    StrokeCap.Round,
                )
            }

            FrostIcon.GRID -> {
                val gap = w * 0.14f
                val cell = (w - gap) / 2f
                val radius = cell * 0.28f
                listOf(
                    Offset(0f, 0f),
                    Offset(cell + gap, 0f),
                    Offset(0f, cell + gap),
                    Offset(cell + gap, cell + gap),
                ).forEach { topLeft ->
                    drawRoundRect(
                        color = tint,
                        topLeft = topLeft,
                        size = Size(cell, cell),
                        cornerRadius = CornerRadius(radius),
                        style = stroke,
                    )
                }
            }

            FrostIcon.COMPASS -> {
                drawCircle(
                    color = tint,
                    radius = w * 0.44f,
                    center = center,
                    style = stroke,
                )
                val needle =
                    Path().apply {
                        moveTo(w * 0.66f, h * 0.34f)
                        lineTo(w * 0.56f, h * 0.56f)
                        lineTo(w * 0.34f, h * 0.66f)
                        lineTo(w * 0.44f, h * 0.44f)
                        close()
                    }
                drawPath(needle, tint, style = stroke)
            }

            FrostIcon.USER -> {
                drawCircle(
                    color = tint,
                    radius = w * 0.19f,
                    center = Offset(w * 0.5f, h * 0.32f),
                    style = stroke,
                )
                drawArc(
                    color = tint,
                    startAngle = 205f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(w * 0.14f, h * 0.50f),
                    size = Size(w * 0.72f, h * 0.66f),
                    style = stroke,
                )
            }
        }
    }
}

/** 通栏内部光衬：左上薄荷绿 + 右上冷蓝，滚动时随模糊一起降级为纯色。 */
private fun DrawScope.drawTopBarSheen(frost: FrostColors) {
    val span = max(size.width, size.height).coerceAtLeast(1f)
    drawRect(
        Brush.radialGradient(
            colors = listOf(frost.glowMint.copy(alpha = 0.42f), Color.Transparent),
            center = Offset(size.width * 0.16f, -size.height * 0.45f),
            radius = span * 1.1f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(frost.glowBlue.copy(alpha = 0.28f), Color.Transparent),
            center = Offset(size.width * 0.95f, size.height * 0.20f),
            radius = span * 0.8f,
        ),
    )
}
