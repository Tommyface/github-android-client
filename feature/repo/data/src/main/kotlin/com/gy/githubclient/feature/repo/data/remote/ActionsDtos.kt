/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data.remote

import com.gy.githubclient.feature.repo.domain.model.WorkflowRun
import com.gy.githubclient.feature.repo.domain.model.WorkflowRunPage
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /repos/{o}/{r}/actions/runs 响应包装。 */
@Serializable
data class WorkflowRunsResponseDto(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("workflow_runs") val workflowRuns: List<WorkflowRunDto> = emptyList(),
)

@Serializable
data class WorkflowRunDto(
    val id: Long = 0L,
    val name: String = "",
    val event: String = "",
    val status: String = "",
    val conclusion: String? = null,
    @SerialName("head_branch") val headBranch: String = "",
    @SerialName("head_sha") val headSha: String = "",
    @SerialName("run_number") val runNumber: Int = 0,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    fun toDomain() =
        WorkflowRun(
            id = id,
            name = name,
            event = event,
            status = status,
            conclusion = conclusion,
            headBranch = headBranch,
            headSha = headSha,
            runNumber = runNumber,
            htmlUrl = htmlUrl,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
}

/** 转换为分页结果：本页条数 < perPage 视为无更多（total_count 仅作参考，GitHub 列表稳定）。 */
fun WorkflowRunsResponseDto.toPage(perPage: Int): WorkflowRunPage =
    WorkflowRunPage(
        runs = workflowRuns.map { it.toDomain() },
        hasMore = workflowRuns.size >= perPage,
    )
