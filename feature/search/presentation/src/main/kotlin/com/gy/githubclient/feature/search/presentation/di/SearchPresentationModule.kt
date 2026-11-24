/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.search.presentation.di

import com.gy.githubclient.feature.search.presentation.SearchViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** search presentation 层 DI：ViewModel。 */
val searchPresentationModule =
    module {
        viewModel { SearchViewModel(search = get(), setStarred = get()) }
    }
