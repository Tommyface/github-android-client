/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.data.worker

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.gy.githubclient.core.domain.network.AssetIdentity
import com.gy.githubclient.core.domain.network.Downloader
import com.gy.githubclient.feature.download.data.download.ApkSignatureVerifier
import com.gy.githubclient.feature.download.data.download.Sha256Verifier
import com.gy.githubclient.feature.download.data.local.DownloadDao
import com.gy.githubclient.feature.download.domain.model.DownloadStatus
import kotlinx.datetime.Clock
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

/** 后台下载 Worker：调用 Downloader，实时更新 Room 任务状态与前台通知。 */
@Suppress("TooGenericExceptionCaught")
class DownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params),
    KoinComponent {
    private val downloader: Downloader by inject()
    private val dao: DownloadDao by inject()

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val url = inputData.getString(KEY_URL) ?: return Result.failure()
        val fileName = inputData.getString(KEY_FILE_NAME) ?: return Result.failure()
        val assetId = inputData.getLong(KEY_ASSET_ID, 0L)
        val totalBytes = inputData.getLong(KEY_TOTAL_BYTES, -1L)
        val expectedSha256 = inputData.getString(KEY_SHA256)

        DownloadNotifications.ensureChannel(applicationContext)
        val notificationId = DownloadNotifications.idFor(taskId)
        val title = fileName.substringBeforeLast('.', fileName)
        setForeground(
            ForegroundInfo(
                notificationId,
                DownloadNotifications.build(applicationContext, title, "准备下载…", null, true),
            ),
        )
        markStatus(taskId, DownloadStatus.DOWNLOADING, null)

        try {
            downloader
                .download(
                    url = url,
                    suggestedFileName = fileName,
                    bypassMirror = false,
                    identity = AssetIdentity(assetId = assetId, size = totalBytes),
                ).collect { progress ->
                    dao.updateProgress(
                        id = taskId,
                        bytes = progress.bytesDownloaded,
                        total = progress.totalBytes,
                        percent = progress.percent,
                        status = DownloadStatus.DOWNLOADING.name,
                        updatedAt = now(),
                    )
                    setForeground(
                        ForegroundInfo(
                            notificationId,
                            DownloadNotifications.build(
                                applicationContext,
                                title,
                                "${progress.percent ?: "?"}%",
                                progress.percent,
                            ),
                        ),
                    )
                }

            val path = downloader.getDownloadedFilePath(fileName)
            if (path == null) {
                markStatus(taskId, DownloadStatus.FAILED, "下载完成但未找到文件")
                return Result.failure()
            }

            // 完整性校验：有官方 sha256 摘要时必须一致；镜像传输被篡改/截断的文件在此拦截。
            if (expectedSha256 != null && !Sha256Verifier.verify(java.io.File(path), expectedSha256)) {
                val actual = Sha256Verifier.digest(java.io.File(path))
                java.io.File(path).delete()
                markStatus(
                    taskId,
                    DownloadStatus.FAILED,
                    "SHA-256 校验失败，文件已删除（预期 $expectedSha256，实际 $actual）",
                )
                setForeground(
                    ForegroundInfo(
                        notificationId,
                        DownloadNotifications.build(applicationContext, title, "校验失败已删除", 100),
                    ),
                )
                return Result.failure()
            }

            // APK 签名指纹校验（v0.5.3 M3）：摘要缺失场景下的防替换兜底，不匹配即删。
            // 期望指纹按构建变体取：debug 用公开调试证书指纹，release 用正式证书指纹（M0-08）。
            if (fileName.endsWith(".apk")) {
                val certHex = apkCertificateHex(path)
                val expected = ApkSignatureVerifier.expectedFor(isDebuggableBuild())
                if (certHex == null || !ApkSignatureVerifier.matches(certHex, expected)) {
                    java.io.File(path).delete()
                    markStatus(taskId, DownloadStatus.FAILED, "APK 签名指纹不匹配（防替换校验），文件已删除")
                    setForeground(
                        ForegroundInfo(
                            notificationId,
                            DownloadNotifications.build(applicationContext, title, "签名不匹配已删除", 100),
                        ),
                    )
                    return Result.failure()
                }
            }

            dao.updateComplete(taskId, path, DownloadStatus.DONE.name, now())
            setForeground(
                ForegroundInfo(
                    notificationId,
                    DownloadNotifications.build(applicationContext, title, "下载完成", 100),
                ),
            )
            return Result.success()
        } catch (e: Exception) {
            markStatus(taskId, DownloadStatus.FAILED, e.message ?: "下载失败")
            return Result.failure()
        }
    }

    private suspend fun markStatus(
        taskId: String,
        status: DownloadStatus,
        error: String?,
    ) {
        dao.updateStatus(taskId, status.name, error, now())
    }

    /** 读取 APK 的签名证书 SHA-256（hex）；解析失败返回 null（调用方按不匹配处理）。 */
    /**
     * 当前安装包是否可调试（debuggable）。
     *
     * 用运行时 FLAG_DEBUGGABLE 而非编译期常量：数据层不应依赖 app 模块，
     * 且这样测试包/正式包各自使用对应的期望证书指纹。
     */
    private fun isDebuggableBuild(): Boolean =
        applicationContext.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0

    private fun apkCertificateHex(apkPath: String): String? {
        val pm = applicationContext.packageManager
        val flags =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                @Suppress("DEPRECATION")
                PackageManager.GET_SIGNATURES
            }
        val info = pm.getPackageArchiveInfo(apkPath, flags) ?: return null
        val signatures =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners?.toList()
            } else {
                @Suppress("DEPRECATION")
                info.signatures?.toList()
            }
        val cert = signatures?.firstOrNull() ?: return null
        return ApkSignatureVerifier.certificateSha256Hex(cert.toByteArray())
    }

    private fun now(): String = Clock.System.now().toString()

    companion object {
        const val KEY_TASK_ID = "taskId"
        const val KEY_URL = "url"
        const val KEY_FILE_NAME = "fileName"
        const val KEY_ASSET_ID = "assetId"
        const val KEY_TOTAL_BYTES = "totalBytes"
        const val KEY_SHA256 = "expectedSha256"
    }
}
