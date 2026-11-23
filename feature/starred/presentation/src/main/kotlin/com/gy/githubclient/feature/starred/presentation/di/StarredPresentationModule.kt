/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.starred.presentation.di

import com.gy.githubclient.feature.starred.presentation.StarredViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** starred presentation 层 DI：ViewModel。 */
val starredPresentationModule =
    module {
        viewModel {
            StarredViewModel(
                observeStarred = get(),
                refreshStarred = get(),
                removeStar = get(),
            )
        }
    }
