/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.presentation.di

import com.gy.githubclient.feature.discover.presentation.DiscoverViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** 发现/商店 presentation 层 DI。 */
val discoverPresentationModule =
    module {
        viewModel { DiscoverViewModel(get(), get(), get(), get(), get(), get()) }
    }
