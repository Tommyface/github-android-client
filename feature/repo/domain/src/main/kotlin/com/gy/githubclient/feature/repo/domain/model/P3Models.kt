/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain.model

/** 仓库标签（v0.17.0 P3-a）：来自 GET /labels，只读展示。 */
data class RepoLabel(
    val name: String,
    /** 6 位十六进制颜色，不含 # 前缀；UI 需自行加 # 解析并兜底。 */
    val color: String,
    val description: String?,
)

/** 里程碑（v0.17.0 P3-b）：来自 GET /milestones?state=all，只读展示。 */
data class Milestone(
    val number: Int,
    val title: String,
    /** "open" | "closed"。 */
    val state: String,
    val description: String?,
    val openIssues: Int,
    val closedIssues: Int,
    val htmlUrl: String,
) {
    val isOpen: Boolean get() = state == "open"

    val totalIssues: Int get() = openIssues + closedIssues
}

/** Fork 条目（v0.17.0 P3-c）：来自 GET /forks，只读展示派生仓库的最小画像。 */
data class ForkItem(
    val fullName: String,
    val ownerLogin: String,
    val avatarUrl: String?,
    val stargazersCount: Int,
    val htmlUrl: String,
    val updatedAt: String?,
)

/**
 * 里程碑完成度百分比（纯函数，可单测）：closed / (open + closed) 向下取整。
 * 无 issue 时返回 0，避免除零；负输入按 0 处理。
 */
fun milestoneProgressPercent(
    open: Int,
    closed: Int,
): Int {
    val openSafe = open.coerceAtLeast(0)
    val closedSafe = closed.coerceAtLeast(0)
    val total = openSafe + closedSafe
    return if (total == 0) 0 else closedSafe * 100 / total
}
