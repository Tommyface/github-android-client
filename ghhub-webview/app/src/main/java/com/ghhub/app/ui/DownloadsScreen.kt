package com.ghhub.app.ui

import android.content.Context
import android.graphics.Color
import android.view.ViewGroup
import android.widget.*

/** S2 下载中心：展示 AppUpdater 承载的下载任务（设计稿 B 屏）。 */
class DownloadsScreen(private val ctx: Context, private val onDownload: (String, String) -> Unit) {
    private val list = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    private val items = mutableListOf<DownloadItem>()

    data class DownloadItem(val name: String, val source: String, val size: String, val status: Status) {
        enum class Status { DOWNLOADING, SWITCHED, DONE }
    }

    val root = UIKit.screenRoot(ctx).apply {
        addView(UIKit.title(ctx, "下载中心"))
        addView(UIKit.label(ctx, "AppUpdater 2.2.0 · 失败自动切源 · MD5/sha256 双重校验").apply {
            setPadding(0, UIKit.dp(ctx, 3), 0, UIKit.dp(ctx, 12))
        })
        val scroll = ScrollView(ctx).apply { addView(list) }
        addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
    }

    init {
        items.addAll(listOf(
            DownloadItem("licai-ledger-v3.2.0.apk", "ghproxy.com · 86ms", "1.8 / 2.6 MB · 4.2 MB/s", DownloadItem.Status.DOWNLOADING),
            DownloadItem("githup-v1.2.20.apk", "直连超时 → ghproxy.com", "直连 github.com 超时（12s）", DownloadItem.Status.SWITCHED),
            DownloadItem("app-updater-demo-2.2.0.zip", "ghproxy.com", "2.6 MB · 08:14 完成", DownloadItem.Status.DONE)
        ))
        render()
    }

    fun addCaptured(url: String, filename: String) {
        items.add(0, DownloadItem(filename, "选源中…", "即将开始下载", DownloadItem.Status.DOWNLOADING))
        render()
        // 真实下载由 MainActivity.performDownload 触发；此处展示卡片即入队
    }

    private fun render() {
        list.removeAllViews()
        items.forEach { item -> list.addView(buildCard(item)) }
        list.addView(TextView(ctx).apply {
            text = "下载 / 进度 / 取消 / MD5·sha256 校验全部由 AppUpdater 承载；失败自动切换下一源（解决 #39），结构化日志一键导出（#41）。"
            textSize = 11f; setTextColor(MainView.amber())
            setPadding(UIKit.dp(ctx, 10), UIKit.dp(ctx, 10), UIKit.dp(ctx, 10), UIKit.dp(ctx, 10))
            setBackgroundColor(Color.parseColor("#1A1505"))
        })
    }

    private fun buildCard(item: DownloadItem): LinearLayout = UIKit.card(ctx).apply {
        addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(TextView(ctx).apply { text = when (item.status) {
                DownloadItem.Status.DOWNLOADING -> "↓"; DownloadItem.Status.SWITCHED -> "!"; DownloadItem.Status.DONE -> "✓"
            }; textSize = 16f; setTextColor(when (item.status) {
                DownloadItem.Status.DOWNLOADING -> MainView.blue(); DownloadItem.Status.SWITCHED -> MainView.amber(); DownloadItem.Status.DONE -> MainView.green()
            }) })
            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(ctx).apply { text = item.name; textSize = 12f; setTextColor(MainView.textPrimary()) })
                addView(TextView(ctx).apply { text = item.size; textSize = 10f; setTextColor(MainView.textMuted()) })
            })
            addView(UIKit.chip(ctx, when (item.status) {
                DownloadItem.Status.DOWNLOADING -> "下载中"; DownloadItem.Status.SWITCHED -> "自动切源"; DownloadItem.Status.DONE -> "校验通过"
            }, when (item.status) {
                DownloadItem.Status.DOWNLOADING -> MainView.surface2(); DownloadItem.Status.SWITCHED -> Color.parseColor("#3A2A08"); DownloadItem.Status.DONE -> Color.parseColor("#0F2E14")
            }, when (item.status) {
                DownloadItem.Status.DOWNLOADING -> MainView.blue(); DownloadItem.Status.SWITCHED -> MainView.amber(); DownloadItem.Status.DONE -> MainView.green()
            }))
        })
        addView(TextView(ctx).apply {
            text = "源 ${item.source}"
            textSize = 10f; setTextColor(MainView.textMuted())
            setPadding(0, UIKit.dp(ctx, 6), 0, 0)
        })
    }
}
