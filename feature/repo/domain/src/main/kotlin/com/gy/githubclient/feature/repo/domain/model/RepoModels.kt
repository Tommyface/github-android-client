/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain.model

/** 仓库列表归属维度，对应 GET /user/repos 的 type 参数。 */
enum class RepoType {
    ALL,
    OWNER,
    PUBLIC,
    PRIVATE,
    MEMBER,
}

/** 排序字段，对应 GET /user/repos 的 sort 参数。 */
enum class RepoSort {
    CREATED,
    UPDATED,
    PUSHED,
    NAME,
}

/** 排序方向。 */
enum class SortDirection {
    ASC,
    DESC,
}

/** 仓库可见性。 */
enum class RepoVisibility {
    PUBLIC,
    PRIVATE,
    INTERNAL,
}

/** 仓库所有者最小画像。 */
data class RepoOwner(
    val login: String,
    val avatarUrl: String?,
    val htmlUrl: String,
)

/** 当前用户对仓库的权限能力。 */
data class RepoPermission(
    val admin: Boolean,
    val maintain: Boolean,
    val push: Boolean,
    val triage: Boolean,
    val pull: Boolean,
)

/** 许可证最小信息。 */
data class LicenseInfo(
    val key: String?,
    val name: String?,
)

/** 仓库领域模型：UI 与缓存共用的最小完备视图。 */
@Suppress("LongParameterList")
data class Repository(
    val id: Long,
    val name: String,
    val fullName: String,
    val owner: RepoOwner,
    val description: String?,
    val htmlUrl: String,
    val isPrivate: Boolean,
    val isFork: Boolean,
    val isArchived: Boolean,
    val defaultBranch: String,
    val language: String?,
    val stargazersCount: Int,
    val forksCount: Int,
    val openIssuesCount: Int,
    val updatedAt: String?,
    val topics: List<String> = emptyList(),
    val visibility: RepoVisibility = RepoVisibility.PUBLIC,
    val license: LicenseInfo? = null,
    val permission: RepoPermission? = null,
    val isStarred: Boolean = false,
)

/** 分支最小信息。 */
data class Branch(
    val name: String,
    val sha: String,
    val isProtected: Boolean,
)

/** 仓库 README（v0.6.0 M6）：原始 Markdown 文本，渲染由 presentation 层负责。 */
data class Readme(
    /** 文件名（README.md / README_zh-CN.md 等）。 */
    val fileName: String,
    /** 已解码的 Markdown 原文。 */
    val content: String,
)

/** Issue / PR 只读列表项。PR 与 Issue 共用端点，以 isPullRequest 区分。 */
data class IssueItem(
    val number: Int,
    val title: String,
    val state: String,
    val isPullRequest: Boolean,
    val author: String,
    val commentCount: Int,
    val createdAt: String?,
    val htmlUrl: String,
)

/** Issue 评论时间线条目。 */
data class IssueComment(
    val author: String,
    val body: String,
    val createdAt: String?,
)

/** Issue / PR 详情（正文 + 评论时间线）。 */
data class IssueDetail(
    val number: Int,
    val title: String,
    val state: String,
    val isPullRequest: Boolean,
    val author: String,
    val body: String?,
    val htmlUrl: String,
    val comments: List<IssueComment>,
)

/** 星标历史单周原始增量（对应 stargazers/history 的一个条目）。 */
data class StarWeekEntry(
    /** 该周起始的 Unix 秒时间戳。 */
    val weekStartEpoch: Long,
    /** 该周新增星标数（GitHub 返回的是周增量，非累计）。 */
    val weeklyStars: Int,
    /** 周日开始的每日增量。 */
    val dailyStars: List<Int> = emptyList(),
)

/** 星标趋势曲线上的一个点：累计到该周的星标总数。 */
data class StarHistoryPoint(
    val weekStartEpoch: Long,
    val weeklyStars: Int,
    val cumulativeStars: Int,
)

/** 星标趋势：points 按时间升序（旧→新）。 */
data class StarHistory(
    val points: List<StarHistoryPoint>,
) {
    /** 曲线末端累计值（即区间内当前总星标数）。 */
    val currentTotal: Int get() = points.lastOrNull()?.cumulativeStars ?: 0

    /** 单周最大增量，供图表纵轴参考。 */
    val peakWeekly: Int get() = points.maxOfOrNull { it.weeklyStars } ?: 0

    companion object {
        /**
         * GitHub stargazers/history 以「最新周在前」返回，且 total 为周增量。
         * 这里反转为时间升序后逐周累加，得到可绘制的累计曲线。
         * 负增量（异常数据）按 0 处理，避免累计值倒退。
         */
        fun fromWeekly(entries: List<StarWeekEntry>): StarHistory {
            var running = 0
            val points =
                entries.asReversed().map { entry ->
                    running += entry.weeklyStars.coerceAtLeast(0)
                    StarHistoryPoint(
                        weekStartEpoch = entry.weekStartEpoch,
                        weeklyStars = entry.weeklyStars,
                        cumulativeStars = running,
                    )
                }
            return StarHistory(points)
        }
    }
}

/** 仓库列表查询条件。 [page] 从 1 开始。 */
@Suppress("LongParameterList")
data class RepoFilter(
    val type: RepoType = RepoType.OWNER,
    val sort: RepoSort = RepoSort.UPDATED,
    val direction: SortDirection = SortDirection.DESC,
    val page: Int = 1,
    val perPage: Int = DEFAULT_PER_PAGE,
) {
    /** 返回下一页条件，供分页加载使用。 */
    fun nextPage(): RepoFilter = copy(page = page + 1)

    companion object {
        const val DEFAULT_PER_PAGE = 30
    }
}
