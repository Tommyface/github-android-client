/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.model.mirror

fun fastestReleaseMirror(
    mirrors: List<MirrorConfig>,
    measuredLatencies: Map<String, Int>,
): Pair<MirrorConfig, Int>? =
    mirrors
        .filter { TrafficKind.RELEASE_ASSET in it.trafficKinds }
        .mapNotNull { mirror -> measuredLatencies[mirror.id]?.let { mirror to it } }
        .minByOrNull { it.second }
