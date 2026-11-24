/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.search.domain.model

/** 搜索类别，对应 GitHub Search API 的五个端点。 */
enum class SearchType(
    val endpoint: String,
) {
    REPOSITORY("repositories"),
    CODE("code"),
    ISSUE("issues"),
    USER("users"),
    COMMIT("commits"),
}

/** 排序方向。 */
enum class SearchOrder(
    val value: String,
) {
    DESC("desc"),
    ASC("asc"),
}

/** 搜索查询条件。代码搜索要认证且限流更严（10 次/分）。 */
data class SearchQuery(
    val term: String,
    val type: SearchType = SearchType.REPOSITORY,
    val sort: String? = null,
    val order: SearchOrder = SearchOrder.DESC,
    val page: Int = 1,
    val perPage: Int = DEFAULT_PER_PAGE,
) {
    /** 空白关键字无效；代码/提交/用户搜索不允许空词。 */
    val isValid: Boolean get() = term.isNotBlank()

    fun nextPage(): SearchQuery = copy(page = page + 1)

    companion object {
        const val DEFAULT_PER_PAGE = 20
    }
}

/** GitHub 搜索响应统一包装：总数 + 是否不完整 + 条目。 */
data class SearchResult<out T>(
    val totalCount: Int,
    val incompleteResults: Boolean,
    val items: List<T>,
    val query: SearchQuery,
) {
    val hasNextPage: Boolean get() = query.page * query.perPage < totalCount
}

/** 异构搜索结果条目，UI 按具体类型渲染不同卡片。 */
sealed interface SearchHit {
    val id: String
    val title: String
    val htmlUrl: String
}

data class RepoHit(
    override val id: String,
    override val title: String,
    override val htmlUrl: String,
    val fullName: String,
    val description: String?,
    val ownerLogin: String,
    val ownerAvatarUrl: String?,
    val language: String?,
    val stargazersCount: Int,
    val forksCount: Int,
    val isPrivate: Boolean,
    val updatedAt: String?,
    /** 仓库主题标签（v0.11.0 N7）：平台启发式推断的数据源之一。 */
    val topics: List<String> = emptyList(),
) : SearchHit

data class CodeHit(
    override val id: String,
    override val title: String,
    override val htmlUrl: String,
    val path: String,
    val repositoryFullName: String,
    val repositoryHtmlUrl: String,
) : SearchHit

data class IssueHit(
    override val id: String,
    override val title: String,
    override val htmlUrl: String,
    val number: Int,
    val state: String,
    val isPullRequest: Boolean,
    val repositoryFullName: String,
    val comments: Int,
    val updatedAt: String?,
) : SearchHit

data class UserHit(
    override val id: String,
    override val title: String,
    override val htmlUrl: String,
    val login: String,
    val avatarUrl: String?,
    val accountType: String,
) : SearchHit

data class CommitHit(
    override val id: String,
    override val title: String,
    override val htmlUrl: String,
    val sha: String,
    val authorName: String?,
    val repositoryFullName: String,
) : SearchHit
