/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.library.data

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.discover.data.remote.GitHubReleaseApi
import com.gy.githubclient.feature.library.data.local.LibraryDao
import com.gy.githubclient.feature.library.data.local.toDomain
import com.gy.githubclient.feature.library.data.local.toEntity
import com.gy.githubclient.feature.library.domain.model.DownloadCandidate
import com.gy.githubclient.feature.library.domain.model.LibraryEntry
import com.gy.githubclient.feature.library.domain.model.TrackedApp
import com.gy.githubclient.feature.library.domain.model.isUpdateAvailable
import com.gy.githubclient.feature.library.domain.model.selectApkAsset
import com.gy.githubclient.feature.library.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 应用库仓库实现：追踪列表落 Room，更新检查复用统一 Release API。 */
class LibraryRepositoryImpl(
    private val dao: LibraryDao,
    private val releaseApi: GitHubReleaseApi,
) : LibraryRepository {
    override fun observeTracked(): Flow<List<TrackedApp>> = dao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun addTracked(app: TrackedApp) = dao.upsert(app.toEntity())

    override suspend fun removeTracked(fullName: String) = dao.remove(fullName)

    override suspend fun checkUpdates(apps: List<TrackedApp>): List<LibraryEntry> =
        apps.map { app -> checkOne(app) }

    private suspend fun checkOne(app: TrackedApp): LibraryEntry =
        when (val result = releaseApi.latestRelease(app.owner, app.name)) {
            is NetworkResult.Success -> {
                val release = result.data
                val apk = selectApkAsset(release.assets, app.abi)
                LibraryEntry(
                    app = app,
                    latestVersion = release.tagName,
                    publishedAt = release.publishedAt,
                    hasUpdate = isUpdateAvailable(release.tagName, app.installedVersion),
                    download =
                        apk?.let {
                            DownloadCandidate(
                                assetName = it.name,
                                url = it.downloadUrl,
                                size = it.size,
                                sha256 = it.sha256,
                            )
                        },
                )
            }

            is NetworkResult.Failure -> LibraryEntry(app = app, error = describeError(result))
        }

    private fun describeError(failure: NetworkResult.Failure): String =
        when (val error = failure.error) {
            com.gy.githubclient.core.domain.network.ApiError.NotFound -> "无公开 Release"
            com.gy.githubclient.core.domain.network.ApiError.Unauthorized -> "登录已失效"
            is com.gy.githubclient.core.domain.network.ApiError.RateLimited -> "API 限流，稍后重试"
            is com.gy.githubclient.core.domain.network.ApiError.Network -> "网络错误：${error.cause}"
            is com.gy.githubclient.core.domain.network.ApiError.Http -> "请求失败（${error.code}）"
            is com.gy.githubclient.core.domain.network.ApiError.Unknown -> error.message
        }
}
