package com.ghhub.app.ui

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.*
import com.ghhub.app.apps.AppRegistry
import com.ghhub.app.apps.AppRowStatus
import com.ghhub.app.apps.ManagedApp
import com.ghhub.app.apps.rowStatus
import com.ghhub.app.core.UpdatePreferences

/** S4 我的应用：多应用版本管理，四行状态（可选/强制/最新/已跳过静默）。 */
class AppsScreen(
    private val ctx: Context,
    private val registry: AppRegistry,
    private val prefs: UpdatePreferences,
    private val onCheck: (String) -> Unit,
    private val onSkip: (String, String, String) -> Unit
) {
    private val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    val root = UIKit.screenRoot(ctx).apply {
        addView(UIKit.title(ctx, "我的应用"))
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(MainView.surface1())
            setPadding(UIKit.dp(ctx, 12), UIKit.dp(ctx, 12), UIKit.dp(ctx, 12), UIKit.dp(ctx, 12))
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(ctx).apply { text = "统一检查更新"; textSize = 13f; setTextColor(MainView.textPrimary()) })
                addView(TextView(ctx).apply {
                    text = "节流窗口 ${prefs.throttleMinutes}min · 双通道并行"; textSize = 10f; setTextColor(MainView.textMuted())
                })
            })
            addView(Button(ctx).apply {
                text = "全部检查"; textSize = 11f
                setBackgroundColor(MainView.green()); setTextColor(MainView.onAccent())
                setOnClickListener { registry.all().forEach { onCheck(it.id) } }
            })
        })
        addView(ScrollView(ctx).apply { addView(list) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun refresh() {
        list.removeAllViews()
        registry.all().forEach { app -> list.addView(buildRow(app)) }
        list.addView(TextView(ctx).apply {
            text = "仅记录「从本应用下载过的 GitHub 应用」；版本号解析失败标记 Unknown 并静默，绝不误报强制更新。"
            textSize = 10f; setTextColor(MainView.textMuted())
            setPadding(UIKit.dp(ctx, 6), UIKit.dp(ctx, 10), UIKit.dp(ctx, 6), 0)
        })
    }

    private fun buildRow(app: ManagedApp): LinearLayout {
        // 按 state 判状态；存在 skip 双键记录即视为 SKIPPED（静默）
        val hasSkip = prefs.getSkipKeys(app.id).isNotEmpty()
        val status = app.rowStatus(hasSkip)
        val (tagText, tagBg, tagFg, sub) = when (status) {
            AppRowStatus.OPTIONAL -> Quad("可选", Color.parseColor("#3A2A08"), MainView.amber(), "v${app.version} → v1.2.21 可选更新")
            AppRowStatus.FORCED -> Quad("强制", Color.parseColor("#3A0E0C"), MainView.red(), "v${app.version} → v2.0.0 强制更新")
            AppRowStatus.LATEST -> Quad("最新", Color.parseColor("#0F2E14"), MainView.green(), "v${app.version} · 已是最新 · 指纹一致")
            AppRowStatus.SKIPPED -> Quad("已静默", MainView.surface3(), MainView.textMuted(), "v${app.version} · 已跳过（skip 记录）")
            AppRowStatus.UNKNOWN -> Quad("Unknown", MainView.surface3(), MainView.textMuted(), "版本号解析失败 · 静默")
        }
        return UIKit.card(ctx).apply {
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                addView(TextView(ctx).apply {
                    text = app.name.take(2).uppercase(); textSize = 12f; setTextColor(Color.parseColor("#04111D"))
                    setBackgroundColor(MainView.blue()); gravity = android.view.Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(UIKit.dp(ctx, 34), UIKit.dp(ctx, 34))
                })
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = UIKit.dp(ctx, 10) }
                    addView(TextView(ctx).apply { text = app.name; textSize = 12f; setTextColor(MainView.textPrimary()) })
                    addView(TextView(ctx).apply { text = sub; textSize = 10f; setTextColor(MainView.textMuted()) })
                })
                addView(UIKit.chip(ctx, tagText, tagBg, tagFg))
            })
            setOnClickListener { onCheck(app.id) }
        }
    }

    private data class Quad(val a: String, val b: Int, val c: Int, val d: String)
}
