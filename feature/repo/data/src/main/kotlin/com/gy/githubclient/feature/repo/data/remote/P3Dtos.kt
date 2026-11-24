/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data.remote

import com.gy.githubclient.feature.repo.domain.model.ForkItem
import com.gy.githubclient.feature.repo.domain.model.Milestone
import com.gy.githubclient.feature.repo.domain.model.RepoLabel
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /labels 单条 DTO（v0.17.0 P3-a）。 */
@Serializable
data class LabelDto(
    val name: String = "",
    val color: String = "",
    val description: String? = null,
) {
    fun toDomain(): RepoLabel =
        RepoLabel(
            name = name,
            color = color,
            description = description,
        )
}

/** GET /milestones 单条 DTO（v0.17.0 P3-b）。 */
@Serializable
data class MilestoneDto(
    val number: Int = 0,
    val title: String = "",
    val state: String = "open",
    val description: String? = null,
    @SerialName("open_issues") val openIssues: Int = 0,
    @SerialName("closed_issues") val closedIssues: Int = 0,
    @SerialName("html_url") val htmlUrl: String = "",
) {
    fun toDomain(): Milestone =
        Milestone(
            number = number,
            title = title,
            state = state,
            description = description,
            openIssues = openIssues,
            closedIssues = closedIssues,
            htmlUrl = htmlUrl,
        )
}

/** GET /forks 单条 DTO（v0.17.0 P3-c）：forks 返回完整仓库对象，这里只取展示所需最小字段。 */
@Serializable
data class ForkRepoDto(
    @SerialName("full_name") val fullName: String = "",
    val owner: OwnerDto = OwnerDto(),
    @SerialName("stargazers_count") val stargazersCount: Int = 0,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    fun toDomain(): ForkItem =
        ForkItem(
            fullName = fullName,
            ownerLogin = owner.login,
            avatarUrl = owner.avatarUrl,
            stargazersCount = stargazersCount,
            htmlUrl = htmlUrl,
            updatedAt = updatedAt,
        )
}
