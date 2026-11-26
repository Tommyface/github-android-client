package com.ghhub.app.ui

import android.content.Context
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/** S1 浏览页：WebView 容器 + 顶部多标签栏 + 镜像徽章。 */
class BrowseScreen(private val ctx: Context) {
    val webContainer = FrameLayout(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f
        )
    }

    val root: LinearLayout = UIKit.screenRoot(ctx).apply {
        addView(TextView(ctx).apply {
            text = "GH Hub  ·  WebView 承载 github.com 全功能"
            textSize = 14f; setTextColor(MainView.textPrimary())
        })
        addView(TextView(ctx).apply {
            text = "地址栏常驻「镜像加速」徽章 · 多标签 · DownloadListener 捕获下载"
            textSize = 11f; setTextColor(MainView.textMuted())
            setPadding(0, UIKit.dp(ctx, 4), 0, UIKit.dp(ctx, 10))
        })
        addView(webContainer)
    }
}
