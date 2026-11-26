/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.di

import com.gy.githubclient.core.data.di.coreNetworkModule
import com.gy.githubclient.feature.auth.data.di.authDataModule
import com.gy.githubclient.feature.auth.presentation.di.authPresentationModule
import com.gy.githubclient.feature.discover.data.di.discoverDataModule
import com.gy.githubclient.feature.discover.presentation.di.discoverPresentationModule
import com.gy.githubclient.feature.download.data.di.downloadDataModule
import com.gy.githubclient.feature.download.presentation.di.downloadPresentationModule
import com.gy.githubclient.feature.library.data.di.libraryDataModule
import com.gy.githubclient.feature.library.presentation.di.libraryPresentationModule
import com.gy.githubclient.feature.profile.data.di.profileDataModule
import com.gy.githubclient.feature.profile.presentation.di.profilePresentationModule
import com.gy.githubclient.feature.repo.data.di.repoDataModule
import com.gy.githubclient.feature.repo.presentation.di.repoPresentationModule
import com.gy.githubclient.feature.search.data.di.searchDataModule
import com.gy.githubclient.feature.search.presentation.di.searchPresentationModule
import com.gy.githubclient.feature.starred.data.di.starredDataModule
import com.gy.githubclient.feature.starred.presentation.di.starredPresentationModule
import com.gy.githubclient.update.UpdateCheckViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** core 层共享依赖：目前只承载应用级 ViewModel。 */
val coreModule =
    module {
        viewModel {
            UpdateCheckViewModel(androidContext(), get())
        }
    }

/** 应用级 DI 汇总：聚合 core 与各 feature 模块。 */
val appModules =
    listOf(
        coreModule,
        coreNetworkModule,
        authDataModule,
        authPresentationModule,
        repoDataModule,
        repoPresentationModule,
        searchDataModule,
        searchPresentationModule,
        discoverDataModule,
        discoverPresentationModule,
        downloadDataModule,
        downloadPresentationModule,
        profileDataModule,
        profilePresentationModule,
        starredDataModule,
        starredPresentationModule,
        libraryDataModule,
        libraryPresentationModule,
    )
