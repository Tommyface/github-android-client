/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.data.di

import androidx.room.Room
import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.data.share.AndroidShareManager
import com.gy.githubclient.core.domain.share.ShareManager
import com.gy.githubclient.feature.discover.data.DiscoverRepositoryImpl
import com.gy.githubclient.feature.discover.data.local.DiscoverDao
import com.gy.githubclient.feature.discover.data.local.DiscoverDatabase
import com.gy.githubclient.feature.discover.data.remote.GitHubReleaseApi
import com.gy.githubclient.feature.discover.domain.repository.DiscoverRepository
import com.gy.githubclient.feature.discover.domain.usecase.BuildShareTextUseCase
import com.gy.githubclient.feature.discover.domain.usecase.GetFeaturedReposUseCase
import com.gy.githubclient.feature.discover.domain.usecase.ListReleasesUseCase
import com.gy.githubclient.feature.discover.domain.usecase.ListTagsUseCase
import com.gy.githubclient.feature.discover.domain.usecase.ObserveDiscoverUseCase
import com.gy.githubclient.feature.discover.domain.usecase.RefreshDiscoverUseCase
import com.gy.githubclient.feature.discover.domain.usecase.ResolveDownloadLinkUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** 发现/商店 data 层 DI：Room + Release API + 分享能力 + 仓储 + 用例。 */
val discoverDataModule =
    module {
        single<ShareManager> { AndroidShareManager(androidContext()) }

        single<DiscoverDatabase> {
            Room
                .databaseBuilder(
                    androidContext(),
                    DiscoverDatabase::class.java,
                    DiscoverDatabase.NAME,
                ).fallbackToDestructiveMigration()
                .build()
        }

        single<DiscoverDao> { get<DiscoverDatabase>().discoverDao() }

        single { GitHubReleaseApi(get<GitHubApiClient>()) }

        single<DiscoverRepository> {
            DiscoverRepositoryImpl(
                dao = get<DiscoverDao>(),
                api = get<GitHubReleaseApi>(),
            )
        }

        factory { ObserveDiscoverUseCase(get<DiscoverRepository>()) }
        factory { RefreshDiscoverUseCase(get<DiscoverRepository>()) }
        factory { GetFeaturedReposUseCase(get<DiscoverRepository>()) }
        factory { ListReleasesUseCase(get<DiscoverRepository>()) }
        factory { ListTagsUseCase(get<DiscoverRepository>()) }
        factory { ResolveDownloadLinkUseCase(get<DiscoverRepository>()) }
        factory { BuildShareTextUseCase(get<DiscoverRepository>()) }
    }
