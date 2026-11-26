package com.ghhub.app.ui

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.*
import com.ghhub.app.mirror.MirrorConfig
import com.ghhub.app.mirror.MirrorSelector

/** E 镜像加速：测速选源排名 + 自定义源入口 + 自动切换（设计稿 E 屏）。 */
class MirrorsScreen(private val ctx: Context, private val config: MirrorConfig, private val onProbe: () -> Unit) {
    private val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val status = TextView(ctx).apply { textSize = 11f; setTextColor(MainView.textMuted()) }

    val root = UIKit.screenRoot(ctx).apply {
        addView(UIKit.title(ctx, "镜像加速"))
        addView(UIKit.label(ctx, "MirrorLatencyProbe · 多源测速 + 自动回退 + 成败权重").apply {
            setPadding(0, UIKit.dp(ctx, 3), 0, UIKit.dp(ctx, 10))
        })
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(status, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            addView(Button(ctx).apply { text = "重新测速"; textSize = 11f; setBackgroundColor(MainView.green()); setTextColor(MainView.onAccent()); setOnClickListener { onProbe() } })
        })
        addView(ScrollView(ctx).apply { addView(list) }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    fun setStatus(text: String) { status.text = text }

    fun refresh() {
        list.removeAllViews()
        MirrorSelector.ranked(config.sources).forEachIndexed { idx, src ->
            val lat = src.latencyMs
            val color = when {
                lat == null -> MainView.textMuted()
                lat < 150 -> MainView.green()
                lat < 280 -> MainView.amber()
                else -> MainView.red()
            }
            val card = UIKit.card(ctx).apply {
                addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    addView(TextView(ctx).apply { text = "${idx + 1}"; textSize = 11f; setTextColor(MainView.textMuted()); layoutParams = LinearLayout.LayoutParams(UIKit.dp(ctx, 18), ViewGroup.LayoutParams.WRAP_CONTENT) })
                    addView(LinearLayout(ctx).apply {
                        orientation = LinearLayout.VERTICAL
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        addView(TextView(ctx).apply { text = src.host; textSize = 12f; setTextColor(MainView.textPrimary()) })
                        addView(TextView(ctx).apply { text = "成功 ${src.successCount} / 失败 ${src.failCount} · 丢包 ${src.lossPercent}%"; textSize = 10f; setTextColor(MainView.textMuted()) })
                    })
                    addView(TextView(ctx).apply {
                        text = if (lat == null) "超时" else "${lat}ms"; textSize = 12f; setTextColor(color)
                    })
                })
            }
            list.addView(card)
        }
        list.addView(Button(ctx).apply {
            text = "＋ 添加自定义加速源（issue #35）"; textSize = 11f
            setBackgroundColor(Color.TRANSPARENT); setTextColor(MainView.textMuted())
            setOnClickListener {
                val input = EditText(ctx).apply { hint = "mirror.example.com" }
                android.app.AlertDialog.Builder(ctx).setTitle("添加自定义加速源")
                    .setMessage("输入镜像域名，加入候选源参与测速排序（issue #35）")
                    .setView(input)
                    .setPositiveButton("确定", { _, _ -> config.addCustom(input.text.toString()); refresh() })
                    .setNegativeButton("取消", null).show()
            }
        })
        list.addView(TextView(ctx).apply {
            text = "权重 = 成功率 ÷ 延迟；失败自动切换下一源（解决 #39），成败记录计入下次选源。"
            textSize = 10f; setTextColor(MainView.textMuted()); setPadding(0, UIKit.dp(ctx, 8), 0, 0)
        })
    }
}
