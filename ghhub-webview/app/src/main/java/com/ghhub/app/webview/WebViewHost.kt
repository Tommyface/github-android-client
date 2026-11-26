package com.ghhub.app.webview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import com.ghhub.app.core.UpdatePreferences
import com.ghhub.app.log.StructuredLogger
import com.ghhub.app.mirror.MirrorConfig
import com.ghhub.app.security.SecureTokenStore
import com.ghhub.app.update.InstallGuard
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * WebView 壳（S1）：直接加载 github.com 网页端 → 天然支持 wiki、多标签、复制链接。
 * 原生层只补下载 / 更新 / 鉴权。
 */
class WebViewHost(
    private val context: Context,
    private val container: ViewGroup,
    val mirrorConfig: MirrorConfig,
    private val tokenStore: SecureTokenStore,
    private val updatePrefs: UpdatePreferences,
    private val logger: StructuredLogger,
    private val onDownloadCaptured: (String, String) -> Unit,
    private val onUpdateRequested: (String) -> Unit,
    private val onInstallBlocked: (InstallGuard.CheckResult) -> Unit = {}
) {
    private val httpClient = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS).build()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val tabs = mutableListOf<Tab>()
    private var activeTab: Tab? = null

    data class Tab(val webView: WebView, val title: String)

    fun createTab(url: String = "https://github.com"): Tab {
        val wv = WebView(context.applicationContext).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                userAgentString = "$userAgentString GHHub/0.1.0 (Android WebView)"
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = WebSettings.LOAD_DEFAULT
            }
            CookieManager.getInstance().setAcceptCookie(true)
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val u = request?.url?.toString() ?: return false
                    // 外部链接走系统浏览器（issue #37），github 域内保持 WebView
                    if (!u.contains("github.com") && !u.contains("githubusercontent.com")) {
                        openExternal(u)
                        return true
                    }
                    return false
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    injectBridge()
                }
            }
            webChromeClient = object : WebChromeClient() {
                override fun onReceivedTitle(view: WebView?, title: String?) {
                    // 标题回调可同步给 UI 标签栏
                }
            }
            // DownloadListener：捕获网页下载 → 转入下载中心（方案架构图）
            setDownloadListener(DownloadListener { url, ua, disposition, mime, length ->
                logger.log(StructuredLogger.Level.INFO, "web_download_captured", mapOf(
                    "url" to url, "mime" to mime, "length" to length
                ))
                val filename = disposition?.substringAfter("filename=", "")
                    ?.trim('"', ' ')?.ifBlank { url.substringAfterLast('/') } ?: "download.bin"
                onDownloadCaptured(url, filename)
            })
        }
        val bridge = JsBridge(context, this, tokenStore, updatePrefs, logger)
        wv.addJavascriptInterface(bridge, JsBridge.NAME)
        val tab = Tab(wv, url)
        tabs.add(tab)
        activeTab = tab
        container.addView(wv)
        wv.loadUrl(url)
        return tab
    }

    private fun injectBridge() {
        // 网页端可通过 window.GHHubBridge 调用原生；此处仅确保注入标记
        activeTab?.webView?.evaluateJavascript("window.__GHHUB_READY__=true;", null)
    }

    fun switchTab(tab: Tab) {
        tabs.forEach { it.webView.visibility = if (it === tab) android.view.View.VISIBLE else android.view.View.GONE }
        activeTab = tab
    }

    fun closeTab(tab: Tab) {
        container.removeView(tab.webView)
        tab.webView.destroy()
        tabs.remove(tab)
        if (activeTab === tab) tabs.firstOrNull()?.let { switchTab(it) }
    }

    fun reload() = activeTab?.webView?.reload()
    fun goBack() { if (activeTab?.webView?.canGoBack() == true) activeTab?.webView?.goBack() }
    fun loadUrl(url: String) = activeTab?.webView?.loadUrl(url)
    fun currentUrl(): String = activeTab?.webView?.url ?: "https://github.com"
    fun tabCount(): Int = tabs.size

    /** JS Bridge: 代理 HTTP（注入 token + 镜像选源）。同步返回，桥方法在线程池线程执行。 */
    fun proxyHttp(url: String, method: String, body: String, token: String?): String {
        val src = com.ghhub.app.mirror.MirrorSelector.best(mirrorConfig.sources)
        val finalUrl = if (src != null && !url.startsWith("https://api.github.com")) mirrorConfig.resolve(src, url) else url
        val b = Request.Builder().url(finalUrl)
        if (!token.isNullOrBlank()) b.header("Authorization", "Bearer $token")
        b.header("User-Agent", "GHHub/0.1.0")
        val req = when (method.uppercase()) {
            "POST" -> b.post(body.toRequestBody("application/json".toMediaType())).build()
            "PUT" -> b.put(body.toRequestBody("application/json".toMediaType())).build()
            "DELETE" -> b.delete().build()
            else -> b.get().build()
        }
        return try {
            httpClient.newCall(req).execute().use { resp ->
                val text = resp.body?.string() ?: ""
                org.json.JSONObject().apply {
                    put("code", resp.code); put("body", text); put("source", src?.host ?: "direct")
                }.toString()
            }
        } catch (e: Exception) {
            org.json.JSONObject().apply { put("code", -1); put("body", e.message ?: "error") }.toString()
        }
    }

    fun requestDownload(url: String, filename: String) {
        mainHandler.post { onDownloadCaptured(url, filename) }
    }

    fun requestCheckUpdate(appId: String) {
        mainHandler.post { onUpdateRequested(appId) }
    }

    fun requestInstall(apkPath: String) {
        mainHandler.post {
            try {
                // 覆盖安装前置守卫：包名/签名/版本码/未知来源权限
                val check = InstallGuard.check(context, apkPath)
                logger.log(StructuredLogger.Level.INFO, "install_guard", mapOf(
                    "code" to check.code, "allowed" to check.allowed, "message" to check.message
                ))
                if (!check.allowed) {
                    onInstallBlocked(check)
                    return@post
                }
                val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", File(apkPath))
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(uri, "application/vnd.android.package-archive")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                logger.log(StructuredLogger.Level.ERROR, "install_failed", mapOf("error" to e.message))
            }
        }
    }

    fun openExternal(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { }
    }

    fun copyToClipboard(text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("GHHub", text))
    }

    fun destroy() {
        tabs.forEach { it.webView.destroy() }
        tabs.clear()
    }
}
