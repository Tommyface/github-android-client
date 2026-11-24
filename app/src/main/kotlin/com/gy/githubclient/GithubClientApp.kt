/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient

import android.app.Application
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.gy.githubclient.di.appModules
import com.gy.githubclient.update.UpdateCheckWorker
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import java.util.concurrent.TimeUnit

class GithubClientApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@GithubClientApp)
            // OAuth client_id 下发给 data 层（OAuthDeviceFlowRepositoryImpl）。
            properties(mapOf("oauth.clientId" to BuildConfig.OAUTH_CLIENT_ID))
            modules(appModules)
        }
        scheduleUpdateCheck()
    }

    /** 自更新检查：每 6 小时一次，KEEP 策略避免重复排队；304 条件请求零配额消耗。 */
    private fun scheduleUpdateCheck() {
        val request =
            PeriodicWorkRequestBuilder<UpdateCheckWorker>(UPDATE_CHECK_INTERVAL_HOURS, TimeUnit.HOURS)
                .build()
        WorkManager
            .getInstance(this)
            .enqueueUniquePeriodicWork(UPDATE_CHECK_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }

    private companion object {
        const val UPDATE_CHECK_WORK_NAME = "update_check"
        const val UPDATE_CHECK_INTERVAL_HOURS = 6L
    }
}
