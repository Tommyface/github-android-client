/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.data.mirror

import com.gy.githubclient.core.data.mirror.BundledMirrors
import com.gy.githubclient.core.domain.model.mirror.MirrorConfig
import com.gy.githubclient.core.domain.model.mirror.MirrorPreference
import com.gy.githubclient.core.domain.model.mirror.MirrorRemoved
import com.gy.githubclient.core.domain.network.MirrorLatencyProbe
import com.gy.githubclient.core.domain.repository.MirrorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * MirrorRepository 实现：目录用内置列表（无远程下发），偏好与测速结果落 DataStore。
 * 远程目录刷新与镜像下架公告在当前版本精简为 no-op（返回成功 / 空流）。
 */
class MirrorRepositoryImpl(
    private val store: MirrorPreferencesStore,
    private val probe: MirrorLatencyProbe,
) : MirrorRepository {
    override fun observeCatalog(): Flow<List<MirrorConfig>> = flowOf(BundledMirrors.ALL)

    override suspend fun refreshCatalog(): Result<Unit> = Result.success(Unit)

    override fun observePreference(): Flow<MirrorPreference> = store.observePreference()

    override suspend fun setPreference(pref: MirrorPreference) = store.setPreference(pref)

    override fun observeMeasuredLatencies(): Flow<Map<String, Int>> = store.observeLatencies()

    override suspend fun measureLatencies(): Result<Map<String, Int>> =
        runCatching {
            val result = probe.probe(BundledMirrors.ALL)
            store.setLatencies(result)
            result
        }

    override fun observeRemovedNotices(): Flow<MirrorRemoved> = flowOf()
}
