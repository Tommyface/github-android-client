/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data.di

import androidx.room.Room
import com.gy.githubclient.core.data.di.API_HTTP_CLIENT
import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.feature.repo.data.RepoRepositoryImpl
import com.gy.githubclient.feature.repo.data.local.RepoDao
import com.gy.githubclient.feature.repo.data.local.RepoDatabase
import com.gy.githubclient.feature.repo.data.remote.GitHubRepoApi
import com.gy.githubclient.feature.repo.data.translation.ReadmeTranslationApi
import com.gy.githubclient.feature.repo.data.translation.TranslationCacheStore
import com.gy.githubclient.feature.repo.data.translation.TranslationRepositoryImpl
import com.gy.githubclient.feature.repo.domain.repository.RepoRepository
import com.gy.githubclient.feature.repo.domain.translation.ClearTranslationCacheUseCase
import com.gy.githubclient.feature.repo.domain.translation.TranslateTextUseCase
import com.gy.githubclient.feature.repo.domain.translation.TranslationRepository
import com.gy.githubclient.feature.repo.domain.usecase.ClearRepoCacheUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ForkRepositoryUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetCheckOverviewUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetCommitDetailUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetFileContentUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetIssueDetailUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetLanguagesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetReadmeUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetRepositoryUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetStarHistoryUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetTreeUseCase
import com.gy.githubclient.feature.repo.domain.usecase.IsStarredUseCase
import com.gy.githubclient.feature.repo.domain.usecase.IsWatchedUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListBranchesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListCommitsUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListContentsUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListContributorsUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListFileCommitsUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListForksUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListIssuesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListLabelsUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListMilestonesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListWorkflowRunsUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ObserveRepositoriesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.RefreshRepositoriesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.SetStarredUseCase
import com.gy.githubclient.feature.repo.domain.usecase.SetWatchedUseCase
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** repo data 层 DI：Room 缓存 + 远程 API + 仓库 + 用例。 */
val repoDataModule =
    module {
        single<RepoDatabase> {
            Room
                .databaseBuilder(
                    androidContext(),
                    RepoDatabase::class.java,
                    RepoDatabase.NAME,
                ).fallbackToDestructiveMigration()
                .build()
        }

        single<RepoDao> { get<RepoDatabase>().repoDao() }

        single { GitHubRepoApi(get<GitHubApiClient>()) }

        single<RepoRepository> {
            RepoRepositoryImpl(
                dao = get<RepoDao>(),
                api = get<GitHubRepoApi>(),
            )
        }

        factory { ObserveRepositoriesUseCase(get<RepoRepository>()) }
        factory { RefreshRepositoriesUseCase(get<RepoRepository>()) }
        factory { GetRepositoryUseCase(get<RepoRepository>()) }
        factory { GetReadmeUseCase(get<RepoRepository>()) }
        factory { GetStarHistoryUseCase(get<RepoRepository>()) }
        factory { ListIssuesUseCase(get<RepoRepository>()) }
        factory { GetIssueDetailUseCase(get<RepoRepository>()) }
        factory { ListBranchesUseCase(get<RepoRepository>()) }
        factory { ListContentsUseCase(get<RepoRepository>()) }
        factory { GetFileContentUseCase(get<RepoRepository>()) }
        factory { ListCommitsUseCase(get<RepoRepository>()) }
        factory { GetCommitDetailUseCase(get<RepoRepository>()) }
        factory { GetCheckOverviewUseCase(get<RepoRepository>()) }
        factory { ListWorkflowRunsUseCase(get<RepoRepository>()) }
        factory { ListFileCommitsUseCase(get<RepoRepository>()) }
        factory { GetTreeUseCase(get<RepoRepository>()) }
        factory { GetLanguagesUseCase(get<RepoRepository>()) }
        factory { ListContributorsUseCase(get<RepoRepository>()) }
        factory { ListLabelsUseCase(get<RepoRepository>()) }
        factory { ListMilestonesUseCase(get<RepoRepository>()) }
        factory { ListForksUseCase(get<RepoRepository>()) }
        factory { SetStarredUseCase(get<RepoRepository>()) }
        factory { IsStarredUseCase(get<RepoRepository>()) }
        factory { IsWatchedUseCase(get<RepoRepository>()) }
        factory { SetWatchedUseCase(get<RepoRepository>()) }
        factory { ForkRepositoryUseCase(get<RepoRepository>()) }
        factory { ClearRepoCacheUseCase(get<RepoRepository>()) }

        // README 翻译（v0.16.0）：复用 core 的匿名 HttpClient（不带 GitHub token），
        // 译文落 SharedPreferences，避免重复消耗免费翻译额度。
        single { TranslationCacheStore(androidContext()) }
        single { ReadmeTranslationApi(get(named(API_HTTP_CLIENT))) }
        single<TranslationRepository> {
            TranslationRepositoryImpl(
                api = get(),
                cache = get(),
            )
        }
        factory { TranslateTextUseCase(get()) }
        factory { ClearTranslationCacheUseCase(get()) }
    }
