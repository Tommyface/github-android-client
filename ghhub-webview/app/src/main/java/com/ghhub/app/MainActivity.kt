package com.ghhub.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.ghhub.app.apps.AppRegistry
import com.ghhub.app.core.UpdateDecider
import com.ghhub.app.core.UpdatePreferences
import com.ghhub.app.core.UpdateState
import com.ghhub.app.download.DownloadGateway
import com.ghhub.app.log.StructuredLogger
import com.ghhub.app.mirror.MirrorConfig
import com.ghhub.app.mirror.MirrorLatencyProbe
import com.ghhub.app.security.SecureTokenStore
import com.ghhub.app.ui.MainView
import com.ghhub.app.update.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 单 Activity 架构（方案 S1）：承载 WebView + 原生四个 Tab。
 * 更新状态机 / 镜像选源 / 下载桥接在此装配。
 */
class MainActivity : AppCompatActivity() {
    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.Main + job)

    private lateinit var mainView: MainView
    private lateinit var tokenStore: SecureTokenStore
    private lateinit var updatePrefs: UpdatePreferences
    private lateinit var mirrorConfig: MirrorConfig
    private lateinit var mirrorProbe: MirrorLatencyProbe
    private lateinit var logger: StructuredLogger
    private lateinit var appRegistry: AppRegistry
    private lateinit var updateChecker: UpdateChecker
    private lateinit var downloadGateway: DownloadGateway

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tokenStore = SecureTokenStore(this)
        updatePrefs = UpdatePreferences(this)
        mirrorConfig = MirrorConfig(this)
        mirrorProbe = MirrorLatencyProbe()
        logger = StructuredLogger.get(this)
        appRegistry = AppRegistry(this)
        updateChecker = UpdateChecker()
        downloadGateway = DownloadGateway(this, mirrorConfig, logger)

        mainView = MainView(this, tokenStore, updatePrefs, mirrorConfig, mirrorProbe,
            logger, appRegistry, scope,
            onCheckUpdate = { appId -> performCheck(appId) },
            onSkip = { appId, version, fp -> performSkip(appId, version, fp) },
            onDownload = { url, name -> performDownload(url, name) },
            onProbe = { scope.launch { probeMirrors() } },
            onExportLog = { shareLog() }
        )
        setContentView(mainView.root)

        requestNotificationPermissionIfNeeded()
        logger.log(StructuredLogger.Level.INFO, "app_start", mapOf("version" to "0.1.0"))
    }

    /** 状态机检查（S3）：节流 → 双通道 → decide → 更新 UI。 */
    private fun performCheck(appId: String) = scope.launch {
        val app = appRegistry.all().find { it.id == appId } ?: return@launch
        mainView.setUpdateState(UpdateState.Checking)
        logger.log(StructuredLogger.Level.INFO, "check_start", mapOf("app" to appId, "throttled" to updatePrefs.isThrottled()))

        if (updatePrefs.isThrottled()) {
            mainView.setUpdateState(UpdateState.NoUpdate)
            mainView.toast("节流窗口内（${updatePrefs.throttleMinutes}min），跳过检查")
            return@launch
        }
        updatePrefs.lastCheckAt = System.currentTimeMillis()

        val remote = updateChecker.check(app.repo, app.versionJsonUrl, tokenStore.getToken())
        if (remote == null) {
            app.state = UpdateState.Unknown()
            mainView.setUpdateState(app.state)
            mainView.toast("检查失败，静默处理（Unknown）")
            return@launch
        }

        val state = UpdateDecider.decide(
            local = UpdateDecider.LocalInfo(app.version, app.fingerprint),
            remote = remote,
            skippedKeys = updatePrefs.getSkipKeys(app.id),
            forceByMajorMinor = UpdateDecider.isMajorOrMinorBump(app.version, remote.version)
        )
        app.state = state
        app.lastCheckedAt = System.currentTimeMillis()
        val apps = appRegistry.all().toMutableList()
        apps.indexOfFirst { it.id == app.id }.let { if (it >= 0) apps[it] = app }
        appRegistry.save(apps)

        mainView.setUpdateState(state)
        mainView.refreshApps()
        logger.log(StructuredLogger.Level.INFO, "check_result", mapOf("app" to appId, "state" to state.name))
    }

    private fun performSkip(appId: String, version: String, fp: String) {
        updatePrefs.addSkipKey(appId, com.ghhub.app.core.SkipKey(version, fp))
        mainView.setUpdateState(UpdateState.NoUpdate)
        mainView.toast("已跳过 $version · 双键记录，不再提醒")
        logger.log(StructuredLogger.Level.INFO, "skip_version", mapOf("app" to appId, "version" to version, "fingerprint" to fp))
    }

    private fun performDownload(url: String, name: String) {
        downloadGateway.download(url, name,
            onState = { state ->
                mainView.setUpdateState(state)
                mainView.toast("下载状态：${state.name}")
            },
            onInstallReady = { apkPath, check ->
                if (check.allowed) {
                    startCoverInstall(apkPath)
                } else {
                    mainView.notifyInstallResult(check)
                }
            }
        )
    }

    /** 覆盖安装：FileProvider 授权系统安装器，系统层强制签名一致性（方案 S3 ReadyToInstall）。 */
    private fun startCoverInstall(apkPath: String) {
        try {
            val file = java.io.File(apkPath)
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            logger.log(StructuredLogger.Level.INFO, "cover_install_launch", mapOf("apk" to file.name))
            startActivity(intent)
        } catch (e: Exception) {
            logger.log(StructuredLogger.Level.ERROR, "cover_install_failed", mapOf("error" to e.message))
            mainView.toast("拉起安装失败：${e.message}")
        }
    }

    private suspend fun probeMirrors() {
        mainView.setProbeStatus("测速中…")
        val results = mirrorProbe.probeAll(mirrorConfig.sources, "glorywork/licai-ledger")
        mainView.refreshMirrors()
        mainView.setProbeStatus("首选：${results.firstOrNull()?.source?.host ?: "无"}（${results.firstOrNull()?.latencyMs ?: "-"}ms）")
        logger.log(StructuredLogger.Level.INFO, "mirror_probe_done", mapOf("count" to results.size))
    }

    private fun shareLog() {
        try {
            val file = logger.exportFile()
            val uri = androidx.core.content.FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
            val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                type = "application/octet-stream"
                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(android.content.Intent.createChooser(intent, "导出诊断日志（issue #41）"))
        } catch (e: Exception) {
            toast("导出失败：${e.message}")
        }
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        super.onDestroy()
        mainView.destroy()
        job.cancel()
    }
}
