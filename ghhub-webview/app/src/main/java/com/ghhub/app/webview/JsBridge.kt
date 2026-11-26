package com.ghhub.app.webview

import android.content.Context
import android.util.Log
import android.webkit.JavascriptInterface
import com.ghhub.app.core.UpdatePreferences
import com.ghhub.app.log.StructuredLogger
import com.ghhub.app.security.SecureTokenStore
import org.json.JSONObject

/**
 * 裁剪版原生桥（方案：照 githup 116 方法裁剪到实际需要的 ~30 个）。
 * 只暴露鉴权 / 网络 / 下载 / 更新 / 诊断 五类，其余交给 WebView 网页端自然支持。
 */
class JsBridge(
    private val context: Context,
    private val host: WebViewHost,
    private val tokenStore: SecureTokenStore,
    private val updatePrefs: UpdatePreferences,
    private val logger: StructuredLogger
) {
    @JavascriptInterface
    fun getSavedToken(): String = tokenStore.getToken() ?: ""

    @JavascriptInterface
    fun setSavedToken(token: String) {
        tokenStore.saveToken(token)
        logger.log(StructuredLogger.Level.INFO, "bridge_set_token", emptyMap())
    }

    @JavascriptInterface
    fun getThrottleMinutes(): Int = updatePrefs.throttleMinutes

    @JavascriptInterface
    fun setThrottleMinutes(minutes: Int) {
        if (minutes in 1..1440) updatePrefs.throttleMinutes = minutes
    }

    @JavascriptInterface
    fun getLastCheckAt(): Long = updatePrefs.lastCheckAt

    /** 网络请求：注入 token + 镜像选源（由宿主 OkHttp 代理，避免 WebView 直连不稳）。 */
    @JavascriptInterface
    fun http(url: String, method: String, body: String): String {
        logger.log(StructuredLogger.Level.INFO, "bridge_http", mapOf("url" to url, "method" to method))
        return host.proxyHttp(url, method, body, tokenStore.getToken())
    }

    /** 下载：交给 DownloadGateway → AppUpdater。 */
    @JavascriptInterface
    fun download(url: String, filename: String) {
        logger.log(StructuredLogger.Level.INFO, "bridge_download", mapOf("url" to url, "filename" to filename))
        host.requestDownload(url, filename)
    }

    @JavascriptInterface
    fun checkUpdate(appId: String) {
        host.requestCheckUpdate(appId)
    }

    @JavascriptInterface
    fun installUpdate(apkPath: String) {
        host.requestInstall(apkPath)
    }

    @JavascriptInterface
    fun skipVersion(appId: String, version: String, fingerprint: String) {
        updatePrefs.addSkipKey(appId, com.ghhub.app.core.SkipKey(version, fingerprint))
        logger.log(StructuredLogger.Level.INFO, "bridge_skip_version", mapOf(
            "app" to appId, "version" to version, "fingerprint" to fingerprint
        ))
    }

    @JavascriptInterface
    fun isSkipped(appId: String, version: String, fingerprint: String): Boolean =
        com.ghhub.app.core.SkipKey(version, fingerprint) in updatePrefs.getSkipKeys(appId)

    @JavascriptInterface
    fun logError(message: String) {
        logger.log(StructuredLogger.Level.ERROR, "bridge_web_error", mapOf("message" to message))
        Log.e("GHHub-Web", message)
    }

    @JavascriptInterface
    fun exportLog(): String = logger.exportFile().absolutePath

    @JavascriptInterface
    fun getAppVersion(): String = "0.1.0"

    @JavascriptInterface
    fun openExternal(url: String) = host.openExternal(url)

    @JavascriptInterface
    fun copyToClipboard(text: String) = host.copyToClipboard(text)

    @JavascriptInterface
    fun getMirrors(): String {
        val arr = org.json.JSONArray()
        host.mirrorConfig.sources.forEach { s ->
            arr.put(JSONObject().apply {
                put("host", s.host); put("latency_ms", s.latencyMs ?: JSONObject.NULL)
                put("loss", s.lossPercent); put("custom", s.custom)
            })
        }
        return arr.toString()
    }

    companion object {
        const val NAME = "GHHubBridge"
    }
}
