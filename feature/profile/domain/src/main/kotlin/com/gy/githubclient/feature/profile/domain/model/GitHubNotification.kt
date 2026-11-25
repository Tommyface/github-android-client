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

    /** 可供用户点击的网页地址（API 地址转 html，githup 通知跳转修正的等价实现）。 */
    val subjectWebUrl: String? get() = NotificationUrls.toWebUrl(subject.url, repoFullName)
}

/**
 * 通知 subject.url 是 REST 地址（api.github.com/...），不能直接当网页打开。
 * 这里做纯字符串转换：Issue / PR / Commit / Discussion 等映射到 github.com 对应页，
 * 认不出来的返回 null（调用方不提供跳转，而不是打开一个 API 401 页面）。
 */
object NotificationUrls {
    private const val API_PREFIX = "https://api.github.com"
    private const val WEB_PREFIX = "https://github.com"

    private data class RepoApiPath(
        val repoUrl: String,
        val kind: String,
        val rest: List<String>,
    )

    fun toWebUrl(
        apiUrl: String?,
        repoFullName: String,
    ): String? {
        val url = apiUrl?.trim().orEmpty()
        if (url.isBlank()) return null
        if (url.startsWith("$WEB_PREFIX/")) return url
        val parsed = parseRepoApiPath(url) ?: return null
        return contentUrl(parsed) ?: fallbackRepo(repoFullName)
    }

    private fun parseRepoApiPath(url: String): RepoApiPath? {
        if (!url.startsWith("$API_PREFIX/")) return null
        val path = url.removePrefix(API_PREFIX).substringBefore('?').substringBefore('#')
        val parts = path.split('/').filter { it.isNotBlank() }
        if (parts.size < 4 || parts[0] != "repos") return null
        val repoUrl = "$WEB_PREFIX/${parts[1]}/${parts[2]}"
        return RepoApiPath(repoUrl, parts[3], parts.drop(4))
    }

    private fun contentUrl(parsed: RepoApiPath): String? {
        val number = parsed.rest.firstOrNull()
        return when (parsed.kind) {
            "issues" -> number?.let { "${parsed.repoUrl}/issues/$it" }
            "pulls" -> number?.let { "${parsed.repoUrl}/pull/$it" }
            "commits" -> number?.let { "${parsed.repoUrl}/commit/$it" }
            "discussions" -> number?.let { "${parsed.repoUrl}/discussions/$it" }
            "releases" -> "${parsed.repoUrl}/releases"
            "check-suites", "check-runs", "actions" -> "${parsed.repoUrl}/actions"
            else -> null
        }
    }

    private fun fallbackRepo(repoFullName: String): String? =
        repoFullName.takeIf { it.isNotBlank() }?.let { "$WEB_PREFIX/$it" }
}
