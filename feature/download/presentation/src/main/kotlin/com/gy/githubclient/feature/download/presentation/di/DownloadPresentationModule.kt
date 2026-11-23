/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.presentation.di

import com.gy.githubclient.feature.download.presentation.DownloadViewModel
import com.gy.githubclient.feature.download.presentation.MirrorSettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** download presentation 层 DI。 */
val downloadPresentationModule =
    module {
        viewModel { DownloadViewModel(get(), get(), get(), get(), get(), get()) }
        viewModel { MirrorSettingsViewModel(get(), get(), get()) }
    }
