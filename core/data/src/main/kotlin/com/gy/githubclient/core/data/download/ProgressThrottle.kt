/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.data.download

import com.gy.githubclient.core.domain.model.installation.DownloadProgress
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

class ProgressThrottle(
    private val minInterval: Duration = DEFAULT_INTERVAL,
    private val timeSource: TimeSource = TimeSource.Monotonic,
) {
    private val startedAt = timeSource.markNow()
    private var lastEmittedAt: Duration? = null
    private var lastPercent: Int? = null
    private var seenAny = false

    fun shouldEmit(progress: DownloadProgress): Boolean {
        val now = startedAt.elapsedNow()
        val percentChanged = progress.percent != lastPercent
        val intervalElapsed = lastEmittedAt?.let { now - it >= minInterval } ?: true

        if (seenAny && !percentChanged && !intervalElapsed) return false

        seenAny = true
        lastEmittedAt = now
        lastPercent = progress.percent
        return true
    }

    private companion object {
        val DEFAULT_INTERVAL = 100.milliseconds
    }
}
