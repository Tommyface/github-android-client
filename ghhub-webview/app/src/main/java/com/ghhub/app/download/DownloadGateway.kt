package com.ghhub.app.download

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.ghhub.app.core.UpdateState
import com.ghhub.app.log.StructuredLogger
import com.ghhub.app.mirror.MirrorConfig
import com.ghhub.app.mirror.MirrorSelector
import com.ghhub.app.mirror.MirrorSource
import com.ghhub.app.update.InstallGuard
import com.king.app.updater.AppUpdater
import com.king.app.updater.listener.SimpleDownloadListener
import java.io.File

/**
 * 下载桥：AppUpdater 2.2.0 承载下载/进度/取消/MD5/安装（方案五·不重写轮子）。
 * 我方只在前面加「镜像选源」与「失败自动切源」。
 */
class DownloadGateway(
    private val context: Context,
    private val mirrorConfig: MirrorConfig,
    private val logger: StructuredLogger
) {
    private var currentUpdater: AppUpdater? = null
    private var currentSource: MirrorSource? = null
    private var onState: ((UpdateState) -> Unit)? = null
    private var onInstallReady: ((String, InstallGuard.CheckResult) -> Unit)? = null

    fun download(
        rawUrl: String,
        filename: String,
        versionCode: Int = 0,
        onState: (UpdateState) -> Unit,
        onInstallReady: (String, InstallGuard.CheckResult) -> Unit = { _, _ -> }
    ) {
        this.onState = onState
        this.onInstallReady = onInstallReady
        val src = MirrorSelector.best(mirrorConfig.sources)
            ?: run {
                logger.log(StructuredLogger.Level.ERROR, "no_available_mirror", mapOf("url" to rawUrl))
                return
            }
        startWith(src, rawUrl, filename, versionCode, retry = 0)
    }

    private fun startWith(src: MirrorSource, rawUrl: String, filename: String, versionCode: Int, retry: Int) {
        currentSource = src
        val finalUrl = mirrorConfig.resolve(src, rawUrl)
        logger.log(StructuredLogger.Level.INFO, "download_start", mapOf(
            "source" to src.host, "url" to finalUrl, "retry" to retry, "latency_ms" to src.latencyMs
        ))
        onState?.invoke(UpdateState.Downloading(filename, src.host))

        try {
            currentUpdater = AppUpdater.Builder(context)
                .setUrl(finalUrl)
                .setFilename(filename)
                .setVersionCode(versionCode)
                .setShowNotification(true)
                .setShowPercentage(true)
                // 不交给 AppUpdater 直接安装：由我方 InstallGuard 校验签名/版本/权限后再装，
                // 保证「覆盖安装」在签名一致、权限就绪的前提下进行（系统安装器仍强制签名校验）。
                .setInstallApk(false)
                .setDownloadListener(object : SimpleDownloadListener() {
                    override fun onStart(url: String) {
                        logger.log(StructuredLogger.Level.INFO, "download_on_start", mapOf("url" to url))
                    }
                    override fun onProgress(progress: Long, total: Long) {
                        logger.log(StructuredLogger.Level.DEBUG, "download_progress", mapOf(
                            "progress" to progress, "total" to total,
                            "percent" to if (total > 0) progress * 100 / total else 0
                        ))
                    }
                    override fun onSuccess(file: File) {
                        mirrorConfig.recordResult(src, true)
                        logger.log(StructuredLogger.Level.INFO, "download_success", mapOf(
                            "source" to src.host, "file" to file.name, "size" to file.length()
                        ))
                        onState?.invoke(UpdateState.ReadyToInstall(file.absolutePath))
                        // 覆盖安装前置校验：签名一致 / 非降级 / 未知来源权限
                        val check = InstallGuard.check(context, file.absolutePath)
                        logger.log(StructuredLogger.Level.INFO, "install_guard", mapOf(
                            "code" to check.code, "allowed" to check.allowed
                        ))
                        onInstallReady?.invoke(file.absolutePath, check)
                    }
                    override fun onError(cause: Throwable) {
                        mirrorConfig.recordResult(src, false)
                        logger.log(StructuredLogger.Level.ERROR, "download_error", mapOf(
                            "source" to src.host, "error" to cause.message, "retry" to retry
                        ))
                        // 失败自动切换下一源（解决 #39），最多重试 2 次
                        if (retry < 2) {
                            val next = MirrorSelector.nextAfter(src, mirrorConfig.sources)
                            if (next != null && next.id != src.id) {
                                logger.log(StructuredLogger.Level.WARN, "auto_switch_mirror", mapOf(
                                    "from" to src.host, "to" to next.host
                                ))
                                startWith(next, rawUrl, filename, versionCode, retry + 1)
                            }
                        }
                    }
                    override fun onCancel() {
                        logger.log(StructuredLogger.Level.WARN, "download_cancel", mapOf("source" to src.host))
                    }
                })
                .build()
            currentUpdater?.start()
        } catch (e: Exception) {
            logger.log(StructuredLogger.Level.ERROR, "download_launch_failed", mapOf("error" to e.message))
        }
    }

    fun cancel() {
        currentUpdater?.stop()
        logger.log(StructuredLogger.Level.WARN, "download_user_cancel", emptyMap())
    }
}
