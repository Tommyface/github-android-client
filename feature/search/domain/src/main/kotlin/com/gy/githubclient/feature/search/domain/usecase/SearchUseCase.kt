/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.search.domain.usecase

import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.search.domain.model.SearchHit
import com.gy.githubclient.feature.search.domain.model.SearchQuery
import com.gy.githubclient.feature.search.domain.model.SearchResult
import com.gy.githubclient.feature.search.domain.repository.SearchRepository

/** 执行搜索；空白关键字在用例层拦截，不发起网络请求。 */
class SearchUseCase(
    private val repository: SearchRepository,
) {
    suspend operator fun invoke(query: SearchQuery): NetworkResult<SearchResult<SearchHit>> {
        if (!query.isValid) {
            return NetworkResult.Failure(ApiError.Http(code = 0, message = "empty query"))
        }
        return repository.search(query)
    }
}
