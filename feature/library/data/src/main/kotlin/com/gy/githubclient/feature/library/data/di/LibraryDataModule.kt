/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.library.data.di

import androidx.room.Room
import com.gy.githubclient.feature.discover.data.remote.GitHubReleaseApi
import com.gy.githubclient.feature.library.data.LibraryRepositoryImpl
import com.gy.githubclient.feature.library.data.local.LibraryDao
import com.gy.githubclient.feature.library.data.local.LibraryDatabase
import com.gy.githubclient.feature.library.domain.repository.LibraryRepository
import com.gy.githubclient.feature.library.domain.usecase.AddTrackedUseCase
import com.gy.githubclient.feature.library.domain.usecase.CheckUpdatesUseCase
import com.gy.githubclient.feature.library.domain.usecase.ObserveTrackedUseCase
import com.gy.githubclient.feature.library.domain.usecase.RemoveTrackedUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** 应用库 data 层 DI：Room + Release API（复用发现层）+ 仓储 + 用例。 */
val libraryDataModule =
    module {
        single<LibraryDatabase> {
            Room
                .databaseBuilder(
                    androidContext(),
                    LibraryDatabase::class.java,
                    LibraryDatabase.NAME,
                ).fallbackToDestructiveMigration()
                .build()
        }

        single<LibraryDao> { get<LibraryDatabase>().libraryDao() }

        single<LibraryRepository> {
            LibraryRepositoryImpl(
                dao = get<LibraryDao>(),
                releaseApi = get<GitHubReleaseApi>(),
            )
        }

        factory { ObserveTrackedUseCase(get<LibraryRepository>()) }
        factory { AddTrackedUseCase(get<LibraryRepository>()) }
        factory { RemoveTrackedUseCase(get<LibraryRepository>()) }
        factory { CheckUpdatesUseCase(get<LibraryRepository>()) }
    }
