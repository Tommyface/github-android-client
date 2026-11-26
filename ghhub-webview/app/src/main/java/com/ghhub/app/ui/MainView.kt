package com.ghhub.app.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.widget.*
import com.ghhub.app.R
import com.ghhub.app.apps.AppRegistry
import com.ghhub.app.core.UpdatePreferences
import com.ghhub.app.core.UpdateState
import com.ghhub.app.log.StructuredLogger
import com.ghhub.app.mirror.MirrorConfig
import com.ghhub.app.mirror.MirrorLatencyProbe
import com.ghhub.app.security.SecureTokenStore
import com.ghhub.app.update.InstallGuard
import com.ghhub.app.webview.WebViewHost
import kotlinx.coroutines.CoroutineScope

/**
 * 主视图容器：底部四 Tab（浏览/下载/应用/设置）+ 更新中心与镜像加速子页。
 * 通过代码构建，保持零外部布局依赖、与设计稿令牌一致。
 */
class MainView(
    private val ctx: Context,
    private val tokenStore: SecureTokenStore,
    private val updatePrefs: UpdatePreferences,
    private val mirrorConfig: MirrorConfig,
    private val mirrorProbe: MirrorLatencyProbe,
    private val logger: StructuredLogger,
    private val appRegistry: AppRegistry,
    private val scope: CoroutineScope,
    private val onCheckUpdate: (String) -> Unit,
    private val onSkip: (String, String, String) -> Unit,
    private val onDownload: (String, String) -> Unit,
    private val onProbe: () -> Unit,
    private val onExportLog: () -> Unit
) : LinearLayout(ctx) {

    val root: View get() = this
    private val contentFrame = FrameLayout(ctx)
    private lateinit var webHost: WebViewHost

    // 子页
    private val browseScreen = BrowseScreen(ctx)
    private val downloadsScreen = DownloadsScreen(ctx, onDownload)
    private val appsScreen = AppsScreen(ctx, appRegistry, updatePrefs, onCheckUpdate, onSkip)
    private val updatesScreen = UpdatesScreen(ctx)
    private val mirrorsScreen = MirrorsScreen(ctx, mirrorConfig, onProbe)
    private val settingsScreen = SettingsScreen(ctx, tokenStore, updatePrefs, logger, onExportLog)

    enum class Tab { BROWSE, DOWNLOADS, APPS, SETTINGS, UPDATES, MIRRORS }
    private var current = Tab.BROWSE

    init {
        orientation = VERTICAL
        setBackgroundColor(bg())
        addView(contentFrame, LayoutParams(MATCH_PARENT, 0, 1f))
        addView(buildBottomNav(), LayoutParams(MATCH_PARENT, dp(58)))

        // 装配 WebView 壳
        webHost = WebViewHost(ctx, browseScreen.webContainer, mirrorConfig, tokenStore, updatePrefs, logger,
            onDownloadCaptured = { url, name ->
                toast("已捕获网页下载：$name → 转入下载中心")
                switchTab(Tab.DOWNLOADS)
                downloadsScreen.addCaptured(url, name)
            },
            onUpdateRequested = { appId -> switchTab(Tab.UPDATES); onCheckUpdate(appId) },
            onInstallBlocked = { result ->
                toast(result.message)
                if (result.needUnknownAppsPermission) {
                    ctx.startActivity(InstallGuard.unknownAppsSettingsIntent(ctx))
                }
            }
        )
        webHost.createTab()

        switchTab(Tab.BROWSE)
    }

    private fun buildBottomNav(): View = LinearLayout(ctx).apply {
        orientation = HORIZONTAL
        setBackgroundColor(surface1())
        gravity = Gravity.CENTER_VERTICAL
        val items = listOf(
            "浏览" to Tab.BROWSE, "下载" to Tab.DOWNLOADS, "应用" to Tab.APPS, "设置" to Tab.SETTINGS
        )
        items.forEach { (label, tab) ->
            val btn = TextView(ctx).apply {
                text = label
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(0, MATCH_PARENT, 1f)
                textSize = 12f
                setTextColor(textMuted())
                setOnClickListener { switchTab(tab) }
            }
            addView(btn)
        }
    }

    fun switchTab(tab: Tab) {
        current = tab
        contentFrame.removeAllViews()
        val view: View = when (tab) {
            Tab.BROWSE -> browseScreen.root
            Tab.DOWNLOADS -> downloadsScreen.root
            Tab.APPS -> appsScreen.root
            Tab.SETTINGS -> settingsScreen.root
            Tab.UPDATES -> updatesScreen.root
            Tab.MIRRORS -> mirrorsScreen.root
        }
        (view.parent as? android.view.ViewGroup)?.removeView(view)
        contentFrame.addView(view, FrameLayout.LayoutParams(MATCH_PARENT, MATCH_PARENT))
        when (tab) {
            Tab.APPS -> appsScreen.refresh()
            Tab.DOWNLOADS -> { }
            Tab.MIRRORS -> mirrorsScreen.refresh()
            Tab.SETTINGS -> settingsScreen.refresh()
            else -> { }
        }
    }

    fun setUpdateState(state: UpdateState) {
        updatesScreen.render(state)
        appsScreen.refresh()
    }

    fun refreshApps() = appsScreen.refresh()
    fun refreshMirrors() = mirrorsScreen.refresh()

    fun setProbeStatus(text: String) = mirrorsScreen.setStatus(text)

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    /** 供外部（Activity）触发覆盖安装提示，例如权限返回后的重试。 */
    fun notifyInstallResult(result: InstallGuard.CheckResult) {
        toast(result.message)
        if (result.needUnknownAppsPermission) ctx.startActivity(InstallGuard.unknownAppsSettingsIntent(ctx))
    }

    fun destroy() = webHost.destroy()

    // ---- 设计令牌（Primer Dark / Light，与 HTML 设计稿一致） ----
    companion object {
        const val MATCH_PARENT = android.view.ViewGroup.LayoutParams.MATCH_PARENT
        fun dp(v: Int) = (v * 2).coerceAtLeast(1) // 简化：mdpi 基准，正式工程用 Resources#getDimension
        fun bg() = Color.parseColor("#0D1117")
        fun surface1() = Color.parseColor("#161B22")
        fun surface2() = Color.parseColor("#1C2330")
        fun surface3() = Color.parseColor("#21283B")
        fun border() = Color.parseColor("#30363D")
        fun textPrimary() = Color.parseColor("#E6EDF3")
        fun textSecondary() = Color.parseColor("#9198A1")
        fun textMuted() = Color.parseColor("#6E7681")
        fun blue() = Color.parseColor("#58A6FF")
        fun green() = Color.parseColor("#3FB950")
        fun amber() = Color.parseColor("#D29922")
        fun red() = Color.parseColor("#F85149")
        fun purple() = Color.parseColor("#BC8CFF")
        fun onAccent() = Color.parseColor("#04160A")
    }
}
