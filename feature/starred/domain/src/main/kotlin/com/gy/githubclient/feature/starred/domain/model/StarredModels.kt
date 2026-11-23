/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.starred.domain.model

/** 星标仓库列表排序字段，对应 GET /user/starred 的 sort 参数。 */
enum class StarredSort {
    CREATED,
    UPDATED,
}

/** 星标仓库查询条件。 [page] 从 1 开始。 */
data class StarredFilter(
    val sort: StarredSort = StarredSort.UPDATED,
    val page: Int = 1,
    val perPage: Int = DEFAULT_PER_PAGE,
) {
    fun nextPage(): StarredFilter = copy(page = page + 1)

    companion object {
        const val DEFAULT_PER_PAGE = 30
    }
}

/** 星标列表加载结果：本次写入条数 + 是否还有下一页。 */
data class StarredPage(
    val loaded: Int,
    val hasMore: Boolean,
)
