/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.model.mirror

sealed interface MirrorPreference {
    data object Direct : MirrorPreference

    data class Selected(
        val id: String,
    ) : MirrorPreference

    data class Custom(
        val template: String,
    ) : MirrorPreference
}
