/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain.model

/*
 * CI 检查状态（v0.13.0 A2）：仓库最近一次提交的 check runs 与 commit statuses。
 * 全部只读，数据来自 GitHub REST check-runs / statuses。
 */

/** check run 结论归一化后的展示状态。 */
enum class CheckConclusion {
    SUCCESS,
    FAILURE,
    NEUTRAL,
    PENDING,
    UNKNOWN,
    ;

    companion object {
        /** GitHub check_runs.conclusion / statuses.state 原始值 → 归一化。 */
        fun from(raw: String?): CheckConclusion =
            when (raw?.lowercase()) {
                "success" -> SUCCESS
                "failure", "error", "timed_out", "action_required", "cancelled", "startup_failure" -> FAILURE
                "neutral", "skipped" -> NEUTRAL
                "queued", "in_progress", "pending", "waiting", "requested" -> PENDING
                else -> UNKNOWN
            }
    }
}

/** 单条 check run（GET /commits/{sha}/check-runs）。 */
data class CheckRun(
    val name: String,
    val status: String,
    val conclusion: String?,
    val htmlUrl: String?,
) {
    val normalized: CheckConclusion get() = CheckConclusion.from(conclusion ?: status)
}

/** 单条 commit status（GET /commits/{sha}/statuses）。 */
data class CommitStatus(
    val context: String,
    val state: String,
    val targetUrl: String?,
) {
    val normalized: CheckConclusion get() = CheckConclusion.from(state)
}

/** 仓库 CI 概览：聚合 check runs + statuses。 */
data class CheckOverview(
    val sha: String,
    val runs: List<CheckRun> = emptyList(),
    val statuses: List<CommitStatus> = emptyList(),
) {
    /** 无 CI 数据（runs 与 statuses 均空）。 */
    val isEmpty: Boolean get() = runs.isEmpty() && statuses.isEmpty()

    /** 总体结论：任一失败→FAILURE，否则任一进行中→PENDING，否则有数据→SUCCESS，空→UNKNOWN。 */
    val overall: CheckConclusion
        get() {
            val all = runs.map { it.normalized } + statuses.map { it.normalized }
            if (all.isEmpty()) return CheckConclusion.UNKNOWN
            if (all.any { it == CheckConclusion.FAILURE }) return CheckConclusion.FAILURE
            if (all.any { it == CheckConclusion.PENDING }) return CheckConclusion.PENDING
            return CheckConclusion.SUCCESS
        }

    companion object {
        val EMPTY = CheckOverview(sha = "", runs = emptyList(), statuses = emptyList())
    }
}
