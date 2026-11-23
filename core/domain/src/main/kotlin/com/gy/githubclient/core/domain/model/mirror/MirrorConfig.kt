/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.model.mirror

import kotlinx.datetime.Instant

data class MirrorConfig(
    val id: String,
    val name: String,
    val urlTemplate: String?,
    val type: MirrorType,
    val status: MirrorStatus,
    val latencyMs: Int?,
    val lastCheckedAt: Instant?,
    val trafficKinds: Set<TrafficKind> = setOf(TrafficKind.RELEASE_ASSET, TrafficKind.RAW_FILE),
)
