/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data.di

import androidx.room.Room
import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.feature.repo.data.RepoRepositoryImpl
import com.gy.githubclient.feature.repo.data.local.RepoDao
import com.gy.githubclient.feature.repo.data.local.RepoDatabase
import com.gy.githubclient.feature.repo.data.remote.GitHubRepoApi
import com.gy.githubclient.feature.repo.domain.repository.RepoRepository
import com.gy.githubclient.feature.repo.domain.usecase.ClearRepoCacheUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ForkRepositoryUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetCheckOverviewUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetCommitDetailUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetFileContentUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetIssueDetailUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetReadmeUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetRepositoryUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetStarHistoryUseCase
import com.gy.githubclient.feature.repo.domain.usecase.IsStarredUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListBranchesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListCommitsUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListContentsUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListFileCommitsUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListIssuesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListWorkflowRunsUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ObserveRepositoriesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.RefreshRepositoriesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.SetStarredUseCase
import org.koin.android.ext.koin.androidContext
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
        factory { SetStarredUseCase(get<RepoRepository>()) }
        factory { IsStarredUseCase(get<RepoRepository>()) }
        factory { ForkRepositoryUseCase(get<RepoRepository>()) }
        factory { ClearRepoCacheUseCase(get<RepoRepository>()) }
    }
