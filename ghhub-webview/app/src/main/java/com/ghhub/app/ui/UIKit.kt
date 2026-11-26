package com.ghhub.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** 轻量 UI 辅助：保持各屏幕代码紧凑、样式令牌统一。 */
object UIKit {
    fun dp(ctx: Context, v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), ctx.resources.displayMetrics
    ).toInt()

    fun title(ctx: Context, text: String, size: Int = 17, color: Int = MainView.textPrimary()): TextView =
        TextView(ctx).apply {
            this.text = text; textSize = size.toFloat(); setTextColor(color)
            typeface = Typeface.DEFAULT_BOLD
        }

    fun label(ctx: Context, text: String, size: Int = 12, color: Int = MainView.textSecondary()): TextView =
        TextView(ctx).apply { this.text = text; textSize = size.toFloat(); setTextColor(color) }

    fun card(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(MainView.surface1())
        val p = dp(ctx, 12)
        setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = dp(ctx, 9) }
    }

    fun chip(ctx: Context, text: String, bg: Int, fg: Int): TextView = TextView(ctx).apply {
        this.text = text; textSize = 10f; setTextColor(fg); setBackgroundColor(bg)
        gravity = Gravity.CENTER
        val p = dp(ctx, 4)
        setPadding(p * 2, p, p * 2, p)
    }

    fun screenRoot(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(MainView.bg())
        val p = dp(ctx, 14)
        setPadding(p, p, p, p)
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
    }
}
