/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain.model

/*
 * Actions workflow run 只读模型（v0.14.0 A3）：GET /repos/{o}/{r}/actions/runs。
 */

/** run 状态（GitHub status 原始值归一化到展示态）。 */
enum class RunStatus {
    COMPLETED,
    IN_PROGRESS,
    QUEUED,
    UNKNOWN,
    ;

    companion object {
        fun from(raw: String?): RunStatus =
            when (raw?.lowercase()) {
                "completed" -> COMPLETED
                "in_progress" -> IN_PROGRESS
                "queued", "waiting", "requested", "pending" -> QUEUED
                else -> UNKNOWN
            }
    }
}

/** 单次 workflow run 列表项（最小完备只读视图）。 */
data class WorkflowRun(
    val id: Long,
    val name: String,
    val event: String,
    val status: String,
    val conclusion: String?,
    val headBranch: String,
    val headSha: String,
    val runNumber: Int,
    val htmlUrl: String,
    val createdAt: String?,
    val updatedAt: String?,
) {
    val shortSha: String get() = headSha.take(7)

    val normalizedStatus: RunStatus get() = RunStatus.from(status)

    val normalizedConclusion: CheckConclusion get() = CheckConclusion.from(conclusion ?: status)
}

/** 分页结果：runs + 是否还有更多。 */
data class WorkflowRunPage(
    val runs: List<WorkflowRun>,
    val hasMore: Boolean,
)
