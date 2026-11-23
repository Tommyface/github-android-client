/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.network

import com.gy.githubclient.core.domain.model.mirror.MirrorConfig

interface MirrorLatencyProbe {
    suspend fun probe(mirrors: List<MirrorConfig>): Map<String, Int>
}
