/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient

import android.app.Application
import com.gy.githubclient.di.appModules
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class GithubClientApp : Application() {
    override fun onCreate() {
        super.onCreate()
        startKoin {
            androidContext(this@GithubClientApp)
            modules(appModules)
        }
    }
}
