/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.domain.usecase

import com.gy.githubclient.core.domain.model.mirror.MirrorConfig
import com.gy.githubclient.core.domain.model.mirror.MirrorPreference
import com.gy.githubclient.core.domain.repository.MirrorRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

/** 镜像设置页聚合视图：镜像目录 + 当前偏好 + 测速结果。 */
data class MirrorSettingsView(
    val mirrors: List<MirrorConfig>,
    val preference: MirrorPreference,
    val latencies: Map<String, Int>,
)

/** 观察镜像设置（三流合并）。 */
class ObserveMirrorSettingsUseCase(
    private val repository: MirrorRepository,
) {
    operator fun invoke(): Flow<MirrorSettingsView> =
        combine(
            repository.observeCatalog(),
            repository.observePreference(),
            repository.observeMeasuredLatencies(),
        ) { mirrors, preference, latencies ->
            MirrorSettingsView(mirrors, preference, latencies)
        }
}

/** 测速所有镜像。 */
class MeasureMirrorLatenciesUseCase(
    private val repository: MirrorRepository,
) {
    suspend operator fun invoke() = repository.measureLatencies()
}

/** 设置镜像偏好。 */
class SetMirrorPreferenceUseCase(
    private val repository: MirrorRepository,
) {
    suspend operator fun invoke(preference: MirrorPreference) = repository.setPreference(preference)
}
