/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.domain.model

/** 用户公开事件（对应 GET /users/{login}/events/public 的最小视图）。 */
@Suppress("LongParameterList")
data class UserEvent(
    val id: String,
    val type: String,
    val repoFullName: String,
    val createdAt: String,
    /** PushEvent 的分支引用（如 refs/heads/main），其它事件为空。 */
    val ref: String = "",
    /** PushEvent 的 head 短 SHA，其它事件为空。 */
    val head: String = "",
) {
    /** 事件的可读中文描述（纯展示逻辑）。 */
    val displayText: String
        get() =
            when (type) {
                "PushEvent" -> {
                    val branch = ref.removePrefix("refs/heads/")
                    val sha = head.take(SHORT_SHA)
                    buildString {
                        append("推送到 $repoFullName")
                        if (branch.isNotBlank()) append(" 的 $branch")
                        if (sha.isNotBlank()) append(" · head: $sha")
                    }
                }

                "WatchEvent" -> "Star 了 $repoFullName"
                "ForkEvent" -> "Fork 了 $repoFullName"
                "CreateEvent" -> "创建了 $repoFullName"
                "IssuesEvent" -> "在 $repoFullName 操作了 Issue"
                "PullRequestEvent" -> "在 $repoFullName 操作了 PR"
                else -> "$type · $repoFullName"
            }

    private companion object {
        const val SHORT_SHA = 7
    }
}
