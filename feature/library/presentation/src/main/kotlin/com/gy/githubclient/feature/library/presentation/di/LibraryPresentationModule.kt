/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.library.presentation.di

import com.gy.githubclient.feature.library.presentation.LibraryViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** 应用库 presentation 层 DI。 */
val libraryPresentationModule =
    module {
        viewModel {
            LibraryViewModel(
                observeTracked = get(),
                addTracked = get(),
                removeTracked = get(),
                checkUpdates = get(),
                enqueueDownload = get(),
            )
        }
    }
