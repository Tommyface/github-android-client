/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 每 6 小时一次的自更新检查（WorkManager 周期任务）：
 * - [UpdateChecker.check] 命中新版本时发本地通知（点击打开应用内「检查更新」页，
 *   由该页完成应用内下载 + SHA-256/签名指纹双校验闭环，不再跳浏览器）。
 * - 预发布开关持久于 SharedPreferences，与手动检查共用同一判定。
 * - Android 13+ 通知权限未授予时静默跳过，不崩溃、不重试。
 * - 检查本身零配额消耗（304 条件请求），失败静默，符合"用户主动、低频、可解释"原则。
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val update = UpdateChecker(applicationContext).check(includePrereleaseFromPrefs()) ?: return Result.success()
        notify(update)
        return Result.success()
    }

    private fun includePrereleaseFromPrefs(): Boolean =
        applicationContext
            .getSharedPreferences(UpdateCheckPrefs.NAME, Context.MODE_PRIVATE)
            .getBoolean(UpdateCheckPrefs.KEY_INCLUDE_PRERELEASE, false)

    private fun notify(update: UpdateChecker.UpdateInfo) {
        val context = applicationContext
        val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // NotificationChannel 为 API 26+；低版本 NotificationCompat 自动走旧通知路径。
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "应用更新", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        // 应用内更新页：完整展示版本/大小/摘要并提供一键下载（校验链闭环）。
        val contentIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(context, com.gy.githubclient.MainActivity::class.java).apply {
                    putExtra(EXTRA_OPEN_UPDATE_CHECK, true)
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val digestHint = update.sha256?.take(DIGEST_HINT_LENGTH) ?: "官方摘要缺失，安装前将做签名指纹校验"
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("GitHub Client 有新版本 ${update.tagName}")
                .setContentText("SHA-256 前 $DIGEST_HINT_LENGTH 位：$digestHint（点击查看并下载）")
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val CHANNEL_ID = "app_updates"
        const val NOTIFICATION_ID = 1001

        /** 摘要提示长度：肉眼比对 Release 页足够，避免通知文案过长。 */
        const val DIGEST_HINT_LENGTH = 16
        const val EXTRA_OPEN_UPDATE_CHECK = "open_update_check"
    }
}

/** 更新检查偏好（预发布开关）的统一存储键：Worker 与手动检查页共用。 */
object UpdateCheckPrefs {
    const val NAME: String = "update_check"
    const val KEY_INCLUDE_PRERELEASE: String = "include_prerelease"
}
