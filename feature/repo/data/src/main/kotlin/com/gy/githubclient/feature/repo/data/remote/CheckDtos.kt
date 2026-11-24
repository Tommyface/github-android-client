/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data.remote

import com.gy.githubclient.feature.repo.domain.model.CheckOverview
import com.gy.githubclient.feature.repo.domain.model.CheckRun
import com.gy.githubclient.feature.repo.domain.model.CommitStatus
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** check-runs 响应包装（GET /commits/{sha}/check-runs）：{total_count, check_runs}。 */
@Serializable
data class CheckRunsResponseDto(
    @SerialName("total_count") val totalCount: Int = 0,
    @SerialName("check_runs") val checkRuns: List<CheckRunDto> = emptyList(),
)

@Serializable
data class CheckRunDto(
    val name: String = "",
    val status: String = "",
    val conclusion: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
) {
    fun toDomain() =
        CheckRun(
            name = name,
            status = status,
            conclusion = conclusion,
            htmlUrl = htmlUrl,
        )
}

/** combined status 响应（GET /commits/{sha}/status）：{state, statuses}。 */
@Serializable
data class CombinedStatusDto(
    val state: String = "",
    val statuses: List<StatusItemDto> = emptyList(),
)

@Serializable
data class StatusItemDto(
    val context: String = "",
    val state: String = "",
    @SerialName("target_url") val targetUrl: String? = null,
) {
    fun toDomain() =
        CommitStatus(
            context = context,
            state = state,
            targetUrl = targetUrl,
        )
}

fun CheckRunsResponseDto.toDomainRuns(): List<CheckRun> = checkRuns.map { it.toDomain() }

fun CombinedStatusDto.toDomainStatuses(): List<CommitStatus> = statuses.map { it.toDomain() }

fun buildCheckOverview(
    sha: String,
    runs: List<CheckRun>,
    statuses: List<CommitStatus>,
): CheckOverview = CheckOverview(sha = sha, runs = runs, statuses = statuses)
