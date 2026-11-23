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
