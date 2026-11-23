/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.share

/** 分享能力接口（移植自 komi-store，接口零外部依赖）。 */
interface ShareManager {
    fun shareText(text: String)

    fun shareFile(
        fileName: String,
        content: String,
        mimeType: String = "application/json",
    )

    fun pickFile(
        mimeType: String = "application/json",
        onResult: (String?) -> Unit,
    )
}
