/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.search.data.di

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.feature.search.data.SearchRepositoryImpl
import com.gy.githubclient.feature.search.data.remote.GitHubSearchApi
import com.gy.githubclient.feature.search.domain.repository.SearchRepository
import com.gy.githubclient.feature.search.domain.usecase.SearchUseCase
import org.koin.dsl.module

/** search data 层 DI：远程 API + 仓库 + 用例。 */
val searchDataModule =
    module {
        single { GitHubSearchApi(get<GitHubApiClient>()) }
        single<SearchRepository> { SearchRepositoryImpl(get<GitHubSearchApi>()) }
        factory { SearchUseCase(get<SearchRepository>()) }
    }
