/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.domain.model

/** 通知主题（Issue / PR / Release / 讨论）。 */
data class NotificationSubject(
    val title: String,
    val type: String,
    /** 主题详情 URL（REST 形如 api.github.com/...），点击时转浏览器打开。 */
    val url: String?,
)

/** GET /notifications 的最小完备视图（v0.6.0 M7）。 */
data class GitHubNotification(
    val id: Long,
    val unread: Boolean,
    /** 触发原因（comment/mention/review_requested 等）。 */
    val reason: String,
    val subject: NotificationSubject,
    /** 所在仓库全名（owner/repo）。 */
    val repoFullName: String,
    val updatedAt: String?,
) {
    /** 通知线程 id：标记已读用（与 id 同源）。 */
    val threadId: Long get() = id
}
