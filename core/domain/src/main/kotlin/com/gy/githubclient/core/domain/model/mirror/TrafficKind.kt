/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.model.mirror

enum class TrafficKind {
    RELEASE_ASSET,
    RAW_FILE,
    ;

    companion object {
        fun fromWire(value: String): TrafficKind? =
            when (value.lowercase()) {
                "release_asset" -> RELEASE_ASSET
                "raw_file" -> RAW_FILE
                else -> null
            }
    }
}
