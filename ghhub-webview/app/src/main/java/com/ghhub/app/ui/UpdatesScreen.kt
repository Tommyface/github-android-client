package com.ghhub.app.ui

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.ghhub.app.core.UpdateState

/** S3 更新中心：8 状态胶囊 + 可选/强制两套对话框（设计稿 C 屏）。 */
class UpdatesScreen(private val ctx: Context) {
    private val strip = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val stateLabel = TextView(ctx).apply { textSize = 11f; setTextColor(MainView.textMuted()) }
    private val sheet = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val states = listOf("Idle", "Checking", "NoUpdate", "OptionalUpdate", "ForcedUpdate", "Unknown", "Downloading", "ReadyToInstall")

    val root = UIKit.screenRoot(ctx).apply {
        addView(UIKit.title(ctx, "更新中心"))
        addView(UIKit.label(ctx, "状态机 · 杜绝 #40 跳过仍提醒 / Unknown 绝不兜底").apply {
            setPadding(0, UIKit.dp(ctx, 3), 0, UIKit.dp(ctx, 8))
        })
        val hscroll = HorizontalScrollView(ctx).apply { addView(strip) }
        addView(hscroll)
        addView(stateLabel.apply { setPadding(0, UIKit.dp(ctx, 8), 0, UIKit.dp(ctx, 8)) })
        addView(ScrollView(ctx).apply { addView(sheet) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun render(state: UpdateState) {
        // 状态胶囊
        strip.removeAllViews()
        states.forEach { s ->
            val active = state.name == s
            val color = when (s) {
                "NoUpdate", "ReadyToInstall" -> MainView.green()
                "OptionalUpdate" -> MainView.amber()
                "ForcedUpdate" -> MainView.red()
                "Checking", "Downloading" -> MainView.blue()
                else -> MainView.textMuted()
            }
            strip.addView(TextView(ctx).apply {
                text = s; textSize = 10f
                setTextColor(if (active) Color.WHITE else color)
                setBackgroundColor(if (active) color else MainView.surface2())
                gravity = Gravity.CENTER
                setPadding(UIKit.dp(ctx, 8), UIKit.dp(ctx, 5), UIKit.dp(ctx, 8), UIKit.dp(ctx, 5))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = UIKit.dp(ctx, 5)
                }
            })
        }
        stateLabel.text = "当前状态：${state.name} · ${state.reason}"

        // 状态卡片
        sheet.removeAllViews()
        when (state) {
            is UpdateState.OptionalUpdate -> sheet.addView(optionalCard(state))
            is UpdateState.ForcedUpdate -> sheet.addView(forcedCard(state))
            is UpdateState.Unknown -> sheet.addView(infoCard("Unknown · 版本解析失败", "静默处理，绝不兜底 0.0.0；不弹窗、不误报强制更新。", MainView.textMuted(), MainView.surface3()))
            is UpdateState.NoUpdate -> sheet.addView(infoCard("NoUpdate · 完全静默", state.reason, MainView.green(), Color.parseColor("#0F2E14")))
            is UpdateState.Downloading -> sheet.addView(infoCard("Downloading", "用户确认 → AppUpdater 下载 + MD5/sha256 校验 · 源 ${state.mirrorHost}", MainView.blue(), MainView.surface2()))
            is UpdateState.ReadyToInstall -> sheet.addView(infoCard("ReadyToInstall", "校验通过 → FileProvider 授权系统安装器 · ${state.apkPath}", MainView.green(), Color.parseColor("#0F2E14")))
            is UpdateState.Checking -> sheet.addView(infoCard("Checking", "双通道并行：Release API + version.json，谁先成功用谁", MainView.blue(), MainView.surface2()))
            is UpdateState.Idle -> sheet.addView(infoCard("Idle", "启动 / 切前台超 30min 触发检查（受节流控制）", MainView.textMuted(), MainView.surface2()))
        }
        sheet.addView(TextView(ctx).apply {
            text = "检查节流：启动 / 切前台超 30min 才触发；skip 按「版本号 + 指纹」双键记录（修复 #40）；指纹不一致即使版本号相同也触发强更。"
            textSize = 10f; setTextColor(MainView.textMuted())
            setPadding(UIKit.dp(ctx, 6), UIKit.dp(ctx, 10), UIKit.dp(ctx, 6), 0)
        })
    }

    private fun optionalCard(s: UpdateState.OptionalUpdate): View = UIKit.card(ctx).apply {
        addView(UIKit.chip(ctx, "OptionalUpdate · 仅补丁版", Color.parseColor("#3A2A08"), MainView.amber()))
        addView(TextView(ctx).apply { text = "有可用更新"; textSize = 14f; setTextColor(MainView.textPrimary()); setPadding(0, UIKit.dp(ctx, 8), 0, 0) })
        addView(TextView(ctx).apply { text = "v${s.current} → v${s.target}（补丁）"; textSize = 11f; setTextColor(MainView.textSecondary()) })
        s.changelog.forEach { line ->
            addView(TextView(ctx).apply { text = "• $line"; textSize = 11f; setTextColor(MainView.textSecondary()); setPadding(UIKit.dp(ctx, 8), UIKit.dp(ctx, 2), 0, 0) })
        }
        addView(TextView(ctx).apply { text = "✓ 指纹一致 · ${s.fingerprint}"; textSize = 10f; setTextColor(MainView.green()); setPadding(0, UIKit.dp(ctx, 8), 0, 0) })
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Button(ctx).apply { text = "立即更新"; textSize = 11f; setBackgroundColor(MainView.green()); setTextColor(MainView.onAccent()); layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f) })
        })
        addView(TextView(ctx).apply {
            text = "跳过后按「版本号 + 指纹」双键记录，将不再提醒 —— 修复 #40 跳过仍提醒。"
            textSize = 10f; setTextColor(MainView.textMuted()); setPadding(0, UIKit.dp(ctx, 6), 0, 0)
        })
    }

    private fun forcedCard(s: UpdateState.ForcedUpdate): View = UIKit.card(ctx).apply {
        addView(UIKit.chip(ctx, "ForcedUpdate · 主/次版本或指纹变化", Color.parseColor("#3A0E0C"), MainView.red()))
        addView(TextView(ctx).apply { text = "必须更新后才能继续使用"; textSize = 14f; setTextColor(MainView.textPrimary()); setPadding(0, UIKit.dp(ctx, 8), 0, 0) })
        addView(TextView(ctx).apply { text = "v${s.current} → v${s.target}（次版本变更）"; textSize = 11f; setTextColor(MainView.textSecondary()) })
        addView(TextView(ctx).apply { text = "⚠ ${s.reason}"; textSize = 10f; setTextColor(MainView.red()); setPadding(0, UIKit.dp(ctx, 8), 0, 0) })
        addView(TextView(ctx).apply {
            text = "强提醒 · 忽略 skip；仅可「稍后提醒」，不做永久跳过。"
            textSize = 10f; setTextColor(MainView.textMuted()); setPadding(0, UIKit.dp(ctx, 6), 0, 0)
        })
    }

    private fun infoCard(title: String, desc: String, color: Int, bg: Int): View = UIKit.card(ctx).apply {
        setBackgroundColor(bg)
        addView(TextView(ctx).apply { text = title; textSize = 13f; setTextColor(color) })
        addView(TextView(ctx).apply { text = desc; textSize = 11f; setTextColor(MainView.textSecondary()); setPadding(0, UIKit.dp(ctx, 6), 0, 0) })
    }
}
