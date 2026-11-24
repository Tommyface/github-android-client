/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation.di

import com.gy.githubclient.feature.repo.presentation.CommitViewModel
import com.gy.githubclient.feature.repo.presentation.FileBrowserViewModel
import com.gy.githubclient.feature.repo.presentation.RepoViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** repo presentation 层 DI：ViewModel。 */
val repoPresentationModule =
    module {
        viewModel {
            RepoViewModel(
                observeRepositories = get(),
                refreshRepositories = get(),
                setStarred = get(),
                forkRepository = get(),
                getRepository = get(),
                listBranches = get(),
                getReadme = get(),
                getStarHistory = get(),
                listIssues = get(),
                getIssueDetail = get(),
                enqueueDownload = get(),
            )
        }
        viewModel { FileBrowserViewModel(get(), get()) }
        viewModel { CommitViewModel(get(), get()) }
    }
