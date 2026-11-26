/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.presentation.theme

import android.graphics.Bitmap
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random

/**
 * 磨砂玻璃性能预算（v0.19.0）。
 *
 * - 同屏动态模糊层 ≤ 2（顶部通栏 + 底部导航），其余表面一律静态半透明；
 * - 滚动速度超过 [DEGRADE_VELOCITY_PX_PER_SECOND] 时半径归零、不透明度提升；
 * - 滚动停止 [DEGRADE_RESET_DELAY_MS] 后恢复模糊，避免频繁重建 RenderEffect 层。
 */
object FrostBudget {
    const val MAX_DYNAMIC_BLUR_LAYERS: Int = 2
    const val DEGRADE_VELOCITY_PX_PER_SECOND: Float = 1200f
    const val DEGRADE_RESET_DELAY_MS: Long = 120L
    const val DEGRADE_ALPHA_BOOST: Float = 1.22f
    const val DEGRADE_MAX_ALPHA: Float = 0.92f
}

/**
 * 滚动降级状态：由 AppScaffold 通过 [LocalFrostMotion] 下发，
 * 玻璃表面只读 [isFastScrolling]，不参与滚动计算本身。
 */
@Stable
class FrostMotionState internal constructor(
    private val scope: CoroutineScope? = null,
) {
    /** 是否处于高速滚动（true 时所有动态模糊层关闭、填充不透明度提升）。 */
    var isFastScrolling: Boolean by mutableStateOf(false)
        private set

    private var resetJob: Job? = null
    private var lastFrameNanos: Long = 0L

    /** 由 [Modifier.trackFrostScroll] 逐帧调用，入参为一个滚动增量（px）。 */
    fun onScrollDelta(delta: Float) {
        val now = System.nanoTime()
        val elapsedMs =
            if (lastFrameNanos == 0L) {
                16f
            } else {
                ((now - lastFrameNanos) / 1_000_000f).coerceIn(4f, 64f)
            }
        lastFrameNanos = now
        if (abs(delta) / elapsedMs * 1000f >= FrostBudget.DEGRADE_VELOCITY_PX_PER_SECOND) {
            isFastScrolling = true
        }
        val jobScope = scope ?: return
        resetJob?.cancel()
        resetJob =
            jobScope.launch {
                delay(FrostBudget.DEGRADE_RESET_DELAY_MS)
                isFastScrolling = false
                lastFrameNanos = 0L
            }
    }
}

/** 当前页面的滚动降级状态；未设置时玻璃表面按"非滚动"处理。 */
val LocalFrostMotion = staticCompositionLocalOf<FrostMotionState?> { null }

/** 噪点贴图（64×64 平铺），只在主题层生成一次，所有玻璃表面共享。 */
val LocalFrostNoise = staticCompositionLocalOf<ImageBitmap?> { null }

@Composable
fun rememberFrostMotionState(): FrostMotionState {
    val scope = rememberCoroutineScope()
    return remember(scope) { FrostMotionState(scope) }
}

@Composable
fun rememberFrostNoiseTexture(): ImageBitmap = remember { createFrostNoiseBitmap() }

/** 把滚动增量喂给 [state]，供玻璃表面做降级判断。 */
fun Modifier.trackFrostScroll(state: FrostMotionState?): Modifier {
    if (state == null) return this
    return nestedScroll(
        object : NestedScrollConnection {
            override fun onPreScroll(
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                state.onScrollDelta(available.y)
                return Offset.Zero
            }
        },
    )
}

/*
 * 关于"实时背景模糊"的实现结论（v0.19.0 修正）：
 *
 * Compose 的 RenderEffect / Modifier.blur 只能模糊「该节点自身绘制的内容」，
 * 无法采样它下方的兄弟节点。把 RenderEffect 加在玻璃层上，被模糊的只有这层
 * 自己画的渐变与噪点，背后的列表内容完全不受影响 —— 有 GPU 开销、无视觉效果。
 *
 * Android 也没有 iOS UIVisualEffectView 那种系统合成的背景模糊：API 31+ 的
 * Window.setBackgroundBlurRadius 只模糊窗口后方的桌面，对应用内滚动内容无效。
 * 若强行把内容层整体模糊再裁切给通栏，等于每帧多一层全屏绘制 + 全屏模糊，
 * 与「滚动降级」的性能目标直接冲突。
 *
 * 因此本方案不启用 renderEffect，改用「高不透明度填充 + 竖向渐变收尾 + 极轻噪点
 * + 1dp 高光描边 + 内阴影」模拟磨砂质感：滚动内容经过通栏下方时被半透明层柔化，
 * 视觉上呈雾面而非锐利穿透，成本为零，且 API 24 与 API 31+ 表现完全一致。
 */

/**
 * Frost 玻璃表面：项目里所有"玻璃"都必须经过这里，以保证
 * 填充 / 描边 / 噪点 / 模糊开关四处行为一致，并让性能预算可被审查。
 *
 * @param blur 层级标记：true 表示"动态层"（顶部通栏 / 底部导航）。v0.19.0 起不产生
 *   renderEffect（原因见上方说明），保留参数用于表达设计层级；列表项与卡片恒为 false。
 */
@Suppress("LongParameterList")
@Composable
fun FrostSurface(
    modifier: Modifier = Modifier,
    level: FrostLevel = FrostLevel.L2,
    shape: Shape = RoundedCornerShape(FrostDimens.CardRadius),
    blur: Boolean = false,
    elevated: Boolean = false,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val frost = LocalFrostColors.current
    val motion = LocalFrostMotion.current
    val fastScrolling = motion?.isFastScrolling == true
    // 滚动降级：不做像素模糊，只把填充不透明度提升，保证滚动时画面不"发飘"。
    val baseFill = frost.fill(level)
    val fill =
        if (fastScrolling) {
            baseFill.copy(alpha = (baseFill.alpha * FrostBudget.DEGRADE_ALPHA_BOOST).coerceAtMost(FrostBudget.DEGRADE_MAX_ALPHA))
        } else {
            baseFill
        }
    val edge = frost.edge(level)
    val edgeBrush =
        remember(edge) {
            Brush.verticalGradient(listOf(edge, edge.copy(alpha = edge.alpha * 0.34f)))
        }
    val noiseBrush = rememberFrostNoiseBrush()
    val noiseAlpha = if (fastScrolling) frost.noiseAlpha * 0.5f else frost.noiseAlpha

    Box(
        modifier =
            modifier
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .then(if (elevated) Modifier.shadowGlass(frost, shape) else Modifier)
                .clip(shape)
                .drawBehind { drawRect(fill) },
    ) {
        // 光衬 + 噪点同层绘制：这是静态雾面质感的主体（见上方关于背景模糊的说明），
        // 不挂任何 renderEffect，零图层开销；滚动时只降低噪点透明度。
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .drawBehind {
                        drawFrostSheen(frost, level)
                        noiseBrush?.let { drawRect(brush = it, alpha = noiseAlpha) }
                    },
        )
        content()
        // 描边发丝线放最后，保证不被内容盖住（1dp，无分割线感）。
        Box(modifier = Modifier.matchParentSize().border(1.dp, edgeBrush, shape))
    }
}

/** 内容卡片：L2 静态玻璃，永不实时模糊（LazyColumn 内逐项零图层开销）。 */
@Composable
fun FrostCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(FrostDimens.CardPaddingH, FrostDimens.CardPaddingV),
    content: @Composable ColumnScope.() -> Unit,
) {
    FrostSurface(
        modifier = modifier.fillMaxWidth(),
        level = FrostLevel.L2,
        shape = RoundedCornerShape(FrostDimens.CardRadius),
        blur = false,
        onClick = onClick,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(contentPadding), content = content)
    }
}

/** 页面底色：多层 radial-gradient 光斑 + 竖向渐变，静态绘制，无模糊层。 */
@Composable
fun Modifier.frostPageBackground(): Modifier {
    val frost = LocalFrostColors.current
    return drawBehind { drawFrostPageBackdrop(frost) }
}

@Composable
fun FrostPageBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxSize().frostPageBackground(), content = content)
}

private fun Modifier.shadowGlass(
    frost: FrostColors,
    shape: Shape,
): Modifier {
    val baseAlpha = if (frost.isDark) 0.26f else 0.07f
    return shadow(
        elevation = 18.dp,
        shape = shape,
        clip = false,
        ambientColor = Color.Black.copy(alpha = baseAlpha),
        spotColor = Color.Black.copy(alpha = baseAlpha * 1.2f),
    )
}

/** 噪点笔刷：整屏共享一张 64×64 贴图，平铺绘制，避免逐帧重建 Shader。 */
@Composable
internal fun rememberFrostNoiseBrush(): ShaderBrush? {
    val noise = LocalFrostNoise.current ?: return null
    return remember(noise) {
        ShaderBrush(ImageShader(noise, TileMode.Repeated, TileMode.Repeated))
    }
}

/** 玻璃内部光衬：左上薄荷绿、右下冷蓝，强度随厚度变化。 */
private fun DrawScope.drawFrostSheen(
    frost: FrostColors,
    level: FrostLevel,
) {
    val intensity =
        when (level) {
            FrostLevel.L1 -> 0.55f
            FrostLevel.L2 -> 0.40f
            FrostLevel.L3 -> 0.60f
        }
    val span = max(size.width, size.height).coerceAtLeast(1f)
    drawRect(
        Brush.radialGradient(
            colors = listOf(frost.glowMint.copy(alpha = intensity), Color.Transparent),
            center = Offset(size.width * 0.08f, -size.height * 0.30f),
            radius = span * 0.95f,
        ),
    )
    drawRect(
        Brush.radialGradient(
            colors = listOf(frost.glowBlue.copy(alpha = intensity * 0.62f), Color.Transparent),
            center = Offset(size.width * 0.92f, size.height * 1.12f),
            radius = span * 0.85f,
        ),
    )
}

/** 页面背景：底色渐变 + 四角光斑（与设计稿 1:1 对应，纯静态）。 */
internal fun DrawScope.drawFrostPageBackdrop(frost: FrostColors) {
    drawRect(
        Brush.linearGradient(
            colors = listOf(frost.pageGradientStart, frost.pageGradientEnd),
            start = Offset.Zero,
            end = Offset(size.width * 0.25f, size.height),
        ),
    )
    drawFrostGlow(frost.glowMint, Offset(0.08f, 0.02f), 0.62f, 0.42f)
    drawFrostGlow(frost.glowBlue, Offset(0.96f, 0.16f), 0.55f, 0.36f)
    drawFrostGlow(frost.glowIndigo, Offset(0.10f, 0.86f), 0.55f, 0.40f)
    drawFrostGlow(frost.glowAmber, Offset(0.92f, 0.74f), 0.42f, 0.30f)
}

private fun DrawScope.drawFrostGlow(
    color: Color,
    centerFraction: Offset,
    widthFraction: Float,
    heightFraction: Float,
) {
    val glowCenter = Offset(size.width * centerFraction.x, size.height * centerFraction.y)
    val radius = max(size.width * widthFraction, size.height * heightFraction).coerceAtLeast(1f)
    drawCircle(
        brush =
            Brush.radialGradient(
                colors = listOf(color, Color.Transparent),
                center = glowCenter,
                radius = radius,
            ),
        radius = radius,
        center = glowCenter,
    )
}

/** 生成 64×64 噪点贴图：单次绘制 API 平铺，避免逐点循环。 */
private fun createFrostNoiseBitmap(size: Int = 64): ImageBitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val random = Random(20260101L)
    for (y in 0 until size) {
        for (x in 0 until size) {
            val alpha = if (random.nextInt(100) < 22) random.nextInt(28, 90) else 0
            val shade = if (random.nextBoolean()) 0xFF else 0x00
            bitmap.setPixel(x, y, (alpha shl 24) or (shade shl 16) or (shade shl 8) or shade)
        }
    }
    return bitmap.asImageBitmap()
}
