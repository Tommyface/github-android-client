/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.data.di

import androidx.room.Room
import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.feature.profile.data.ProfileRepositoryImpl
import com.gy.githubclient.feature.profile.data.local.ProfileDao
import com.gy.githubclient.feature.profile.data.local.ProfileDatabase
import com.gy.githubclient.feature.profile.data.remote.GitHubUserApi
import com.gy.githubclient.feature.profile.domain.repository.ProfileRepository
import com.gy.githubclient.feature.profile.domain.usecase.ClearProfileCacheUseCase
import com.gy.githubclient.feature.profile.domain.usecase.ListNotificationsUseCase
import com.gy.githubclient.feature.profile.domain.usecase.MarkNotificationReadUseCase
import com.gy.githubclient.feature.profile.domain.usecase.ObserveProfileUseCase
import com.gy.githubclient.feature.profile.domain.usecase.RefreshProfileUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** profile data 层 DI：Room 缓存 + 远程 API + 仓库 + 用例。 */
val profileDataModule =
    module {
        single<ProfileDatabase> {
            Room
                .databaseBuilder(
                    androidContext(),
                    ProfileDatabase::class.java,
                    ProfileDatabase.NAME,
                ).fallbackToDestructiveMigration()
                .build()
        }

        single<ProfileDao> { get<ProfileDatabase>().profileDao() }

        single { GitHubUserApi(get<GitHubApiClient>()) }

        single<ProfileRepository> {
            ProfileRepositoryImpl(
                dao = get<ProfileDao>(),
                api = get<GitHubUserApi>(),
            )
        }

        factory { ObserveProfileUseCase(get<ProfileRepository>()) }
        factory { RefreshProfileUseCase(get<ProfileRepository>()) }
        factory { ClearProfileCacheUseCase(get<ProfileRepository>()) }
        factory { ListNotificationsUseCase(get<ProfileRepository>()) }
        factory { MarkNotificationReadUseCase(get<ProfileRepository>()) }
    }
