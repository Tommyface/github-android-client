/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.data.di

import androidx.room.Room
import com.gy.githubclient.core.data.mirror.MirrorLatencyProbeImpl
import com.gy.githubclient.core.domain.network.Downloader
import com.gy.githubclient.core.domain.network.MirrorLatencyProbe
import com.gy.githubclient.core.domain.repository.MirrorRepository
import com.gy.githubclient.feature.download.data.DownloadManagerImpl
import com.gy.githubclient.feature.download.data.download.DownloadFileStore
import com.gy.githubclient.feature.download.data.download.OkHttpDownloader
import com.gy.githubclient.feature.download.data.local.DownloadDao
import com.gy.githubclient.feature.download.data.local.DownloadDatabase
import com.gy.githubclient.feature.download.data.local.MIGRATION_1_2
import com.gy.githubclient.feature.download.data.mirror.MirrorPreferencesStore
import com.gy.githubclient.feature.download.data.mirror.MirrorRepositoryImpl
import com.gy.githubclient.feature.download.domain.repository.DownloadManager
import com.gy.githubclient.feature.download.domain.usecase.CancelDownloadUseCase
import com.gy.githubclient.feature.download.domain.usecase.ClearCompletedDownloadsUseCase
import com.gy.githubclient.feature.download.domain.usecase.EnqueueDownloadUseCase
import com.gy.githubclient.feature.download.domain.usecase.MeasureMirrorLatenciesUseCase
import com.gy.githubclient.feature.download.domain.usecase.ObserveDownloadStatsUseCase
import com.gy.githubclient.feature.download.domain.usecase.ObserveDownloadsUseCase
import com.gy.githubclient.feature.download.domain.usecase.ObserveMirrorSettingsUseCase
import com.gy.githubclient.feature.download.domain.usecase.RemoveDownloadUseCase
import com.gy.githubclient.feature.download.domain.usecase.RetryDownloadUseCase
import com.gy.githubclient.feature.download.domain.usecase.SetMirrorPreferenceUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import java.util.concurrent.TimeUnit

/** download data 层 DI：下载引擎 + Room + WorkManager 门面 + 镜像仓库 + 用例。 */
val downloadDataModule =
    module {
        single<OkHttpClient> {
            OkHttpClient
                .Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.MILLISECONDS) // 下载大文件不设读超时
                .writeTimeout(30, TimeUnit.SECONDS)
                .followRedirects(true)
                .build()
        }

        single { DownloadFileStore(androidContext()) }

        // 镜像测速专用 Ktor client（无 token，短超时），与 core 的 API client（带 qualifier）隔离。
        single { HttpClient(OkHttp) { expectSuccess = false } }

        single<MirrorLatencyProbe> { MirrorLatencyProbeImpl(get<HttpClient>()) }

        single { MirrorPreferencesStore(androidContext()) }

        single<MirrorRepository> {
            MirrorRepositoryImpl(
                store = get<MirrorPreferencesStore>(),
                probe = get<MirrorLatencyProbe>(),
            )
        }

        single<Downloader> {
            OkHttpDownloader(
                httpClient = get<OkHttpClient>(),
                fileStore = get<DownloadFileStore>(),
                mirrorRepository = get<MirrorRepository>(),
            )
        }

        single<DownloadDatabase> {
            Room
                .databaseBuilder(
                    androidContext(),
                    DownloadDatabase::class.java,
                    DownloadDatabase.NAME,
                ).addMigrations(MIGRATION_1_2)
                // 兜底仍保留：仅当出现"未声明的版本跳跃"时才会触发，正常升级路径走上方 Migration。
                // 门禁 RoomSchemaGateTest 会保证 version ≥ 2 的库必然有对应 Migration。
                .fallbackToDestructiveMigration()
                .build()
        }

        single<DownloadDao> { get<DownloadDatabase>().downloadDao() }

        single<DownloadManager> {
            DownloadManagerImpl(
                context = androidContext(),
                dao = get<DownloadDao>(),
            )
        }

        factory { ObserveDownloadsUseCase(get<DownloadManager>()) }
        factory { ObserveDownloadStatsUseCase(get<DownloadManager>()) }
        factory { EnqueueDownloadUseCase(get<DownloadManager>()) }
        factory { CancelDownloadUseCase(get<DownloadManager>()) }
        factory { RetryDownloadUseCase(get<DownloadManager>()) }
        factory { RemoveDownloadUseCase(get<DownloadManager>()) }
        factory { ClearCompletedDownloadsUseCase(get<DownloadManager>()) }
        factory { ObserveMirrorSettingsUseCase(get<MirrorRepository>()) }
        factory { MeasureMirrorLatenciesUseCase(get<MirrorRepository>()) }
        factory { SetMirrorPreferenceUseCase(get<MirrorRepository>()) }
    }
