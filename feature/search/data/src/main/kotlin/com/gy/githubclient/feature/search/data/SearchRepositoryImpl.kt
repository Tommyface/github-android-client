/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.search.data

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.search.data.remote.GitHubSearchApi
import com.gy.githubclient.feature.search.domain.model.SearchHit
import com.gy.githubclient.feature.search.domain.model.SearchQuery
import com.gy.githubclient.feature.search.domain.model.SearchResult
import com.gy.githubclient.feature.search.domain.repository.SearchRepository

/** 搜索仓库实现：纯网络，结果由 ViewModel 在内存维护（搜索时效性优先，不入 Room）。 */
class SearchRepositoryImpl(
    private val api: GitHubSearchApi,
) : SearchRepository {
    override suspend fun search(query: SearchQuery): NetworkResult<SearchResult<SearchHit>> = api.search(query)
}
