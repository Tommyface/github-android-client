/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.starred.data.di

import androidx.room.Room
import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.feature.starred.data.StarredRepositoryImpl
import com.gy.githubclient.feature.starred.data.local.StarredDao
import com.gy.githubclient.feature.starred.data.local.StarredDatabase
import com.gy.githubclient.feature.starred.data.remote.GitHubStarredApi
import com.gy.githubclient.feature.starred.domain.repository.StarredRepository
import com.gy.githubclient.feature.starred.domain.usecase.ClearStarredCacheUseCase
import com.gy.githubclient.feature.starred.domain.usecase.ObserveStarredUseCase
import com.gy.githubclient.feature.starred.domain.usecase.RefreshStarredUseCase
import com.gy.githubclient.feature.starred.domain.usecase.RemoveStarUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** starred data 层 DI：Room 缓存 + 远程 API + 仓库 + 用例。 */
val starredDataModule =
    module {
        single<StarredDatabase> {
            Room
                .databaseBuilder(
                    androidContext(),
                    StarredDatabase::class.java,
                    StarredDatabase.NAME,
                ).fallbackToDestructiveMigration()
                .build()
        }

        single<StarredDao> { get<StarredDatabase>().starredDao() }

        single { GitHubStarredApi(get<GitHubApiClient>()) }

        single<StarredRepository> {
            StarredRepositoryImpl(
                dao = get<StarredDao>(),
                api = get<GitHubStarredApi>(),
            )
        }

        factory { ObserveStarredUseCase(get<StarredRepository>()) }
        factory { RefreshStarredUseCase(get<StarredRepository>()) }
        factory { RemoveStarUseCase(get<StarredRepository>()) }
        factory { ClearStarredCacheUseCase(get<StarredRepository>()) }
    }
