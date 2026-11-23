/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.data.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat

/** 下载前台通知：渠道创建 + ForegroundInfo 构建。 */
object DownloadNotifications {
    private const val CHANNEL_ID = "downloads"
    private const val CHANNEL_NAME = "下载"
    const val NOTIFICATION_ID_BASE = 2000

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel =
            NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW).apply {
                description = "Release 资产下载进度"
            }
        manager.createNotificationChannel(channel)
    }

    fun build(
        context: Context,
        title: String,
        content: String,
        percent: Int?,
        indeterminate: Boolean = false,
    ) = NotificationCompat
        .Builder(context, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle(title)
        .setContentText(content)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .setProgress(100, percent ?: 0, indeterminate || percent == null)
        .build()

    fun idFor(taskId: String): Int = NOTIFICATION_ID_BASE + (taskId.hashCode().takeIf { it >= 0 } ?: -taskId.hashCode()) % 10000
}
