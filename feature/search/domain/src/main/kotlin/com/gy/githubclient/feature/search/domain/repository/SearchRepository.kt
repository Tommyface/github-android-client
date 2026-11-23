/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.search.domain.repository

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.search.domain.model.SearchHit
import com.gy.githubclient.feature.search.domain.model.SearchQuery
import com.gy.githubclient.feature.search.domain.model.SearchResult

/**
 * 搜索仓库契约。搜索结果不做 Room 持久化（时效性优先 + Search 独立限流），
 * 由 ViewModel 在内存中维护分页与查询状态。
 */
interface SearchRepository {
    /** 按条件搜索，返回该页结果。 */
    suspend fun search(query: SearchQuery): NetworkResult<SearchResult<SearchHit>>
}
