package com.ghhub.app.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.ghhub.app.core.UpdatePreferences
import com.ghhub.app.log.StructuredLogger
import com.ghhub.app.security.SecureTokenStore

/** F 设置与诊断：Token / 校验 / 节流 / 双通道 / 一键导出（设计稿 F 屏）。 */
class SettingsScreen(
    private val ctx: Context,
    private val tokenStore: SecureTokenStore,
    private val prefs: UpdatePreferences,
    private val logger: StructuredLogger,
    private val onExport: () -> Unit
) {
    private val rows = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    val root = UIKit.screenRoot(ctx).apply {
        addView(UIKit.title(ctx, "设置"))
        addView(UIKit.label(ctx, "复用策略 · 结构化日志一键导出（#41）").apply {
            setPadding(0, UIKit.dp(ctx, 3), 0, UIKit.dp(ctx, 10))
        })
        addView(ScrollView(ctx).apply { addView(rows) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun refresh() {
        rows.removeAllViews()
        rows.addView(section("安全"))
        rows.addView(row("GitHub Token", "SecureTokenStore · EncryptedSharedPreferences + AES256", tokenStore.maskedToken()) {
            val input = EditText(ctx).apply { hint = "ghp_xxxxxxxxxxxx" }
            android.app.AlertDialog.Builder(ctx).setTitle("GitHub Token")
                .setMessage("Token 使用 EncryptedSharedPreferences + AES256 加密存储")
                .setView(input)
                .setPositiveButton("保存", { _, _ -> tokenStore.saveToken(input.text.toString()); refresh() })
                .setNegativeButton("取消", null).show()
        })
        rows.addView(row("下载来源校验", "MD5 / sha256 双重校验，指纹不符即拦截", if (true) "已开启" else "已关闭", null))
        rows.addView(section("更新"))
        rows.addView(row("检查节流窗口", "checks_rate_limit_secs（借鉴 gh_auto_updater）", "${prefs.throttleMinutes} min") {
            val input = EditText(ctx).apply { inputType = android.text.InputType.TYPE_CLASS_NUMBER; setText(prefs.throttleMinutes.toString()) }
            android.app.AlertDialog.Builder(ctx).setTitle("检查节流窗口")
                .setMessage("输入分钟数（1–1440）")
                .setView(input)
                .setPositiveButton("保存", { _, _ ->
                    val n = input.text.toString().toIntOrNull()
                    if (n != null && n in 1..1440) { prefs.throttleMinutes = n; refresh() }
                    else Toast.makeText(ctx, "请输入 1–1440 之间的整数", Toast.LENGTH_SHORT).show()
                })
                .setNegativeButton("取消", null).show()
        })
        rows.addView(row("更新通道", "Release API + version.json 双通道并行", "双通道", null))
        rows.addView(section("诊断"))
        rows.addView(row("结构化日志", "下载链路全量 · JSONL · ${logger.readAll().size} 条", "预览", null))
        rows.addView(Button(ctx).apply {
            text = "一键导出诊断日志（issue #41）"; textSize = 11f
            setBackgroundColor(MainView.surface2()); setTextColor(MainView.blue())
            setOnClickListener { onExport() }
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = UIKit.dp(ctx, 8) }
        })
        rows.addView(TextView(ctx).apply {
            text = "发布工程：一键脚本 + Actions 自动发 Release（参考 githup release.sh）。"
            textSize = 10f; setTextColor(MainView.textMuted()); setPadding(0, UIKit.dp(ctx, 10), 0, 0)
        })
    }

    private fun section(text: String): TextView = TextView(ctx).apply {
        this.text = text; textSize = 11f; setTextColor(MainView.textMuted())
        setPadding(0, UIKit.dp(ctx, 10), 0, UIKit.dp(ctx, 4))
    }

    private fun row(title: String, sub: String, value: String, click: (() -> Unit)?): View = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        setBackgroundColor(MainView.surface1())
        setPadding(UIKit.dp(ctx, 12), UIKit.dp(ctx, 11), UIKit.dp(ctx, 12), UIKit.dp(ctx, 11))
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(ctx).apply { text = title; textSize = 12f; setTextColor(MainView.textPrimary()) })
            addView(TextView(ctx).apply { text = sub; textSize = 9.5f; setTextColor(MainView.textMuted()) })
        })
        addView(TextView(ctx).apply { text = value; textSize = 10.5f; setTextColor(MainView.green()) })
        if (click != null) setOnClickListener { click() }
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = UIKit.dp(ctx, 2) }
    }
}
