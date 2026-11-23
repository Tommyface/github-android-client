/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.repository

import com.gy.githubclient.core.domain.model.mirror.MirrorConfig
import com.gy.githubclient.core.domain.model.mirror.MirrorPreference
import com.gy.githubclient.core.domain.model.mirror.MirrorRemoved
import kotlinx.coroutines.flow.Flow

interface MirrorRepository {
    fun observeCatalog(): Flow<List<MirrorConfig>>

    suspend fun refreshCatalog(): Result<Unit>

    fun observePreference(): Flow<MirrorPreference>

    suspend fun setPreference(pref: MirrorPreference)

    fun observeMeasuredLatencies(): Flow<Map<String, Int>>

    suspend fun measureLatencies(): Result<Map<String, Int>>

    fun observeRemovedNotices(): Flow<MirrorRemoved>
}
