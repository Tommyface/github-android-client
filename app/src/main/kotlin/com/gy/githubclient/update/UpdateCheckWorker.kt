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
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * 每 6 小时一次的自更新检查（WorkManager 周期任务）：
 * - [UpdateChecker.check] 命中新版本时发本地通知（点击打开主界面 / 下载页）。
 * - Android 13+ 通知权限未授予时静默跳过，不崩溃、不重试。
 * - 检查本身零配额消耗（304 条件请求），失败静默，符合"用户主动、低频、可解释"原则。
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val update = UpdateChecker(applicationContext).check() ?: return Result.success()
        notify(update)
        return Result.success()
    }

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
        val contentIntent =
            PendingIntent.getActivity(
                context,
                0,
                Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl ?: update.htmlUrl)),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("GitHub Client 有新版本 ${update.tagName}")
                .setContentText("点击下载最新 APK")
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }

    private companion object {
        const val CHANNEL_ID = "app_updates"
        const val NOTIFICATION_ID = 1001
    }
}
