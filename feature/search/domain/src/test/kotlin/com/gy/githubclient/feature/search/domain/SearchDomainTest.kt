/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.search.domain

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.search.domain.model.SearchHit
import com.gy.githubclient.feature.search.domain.model.SearchQuery
import com.gy.githubclient.feature.search.domain.model.SearchResult
import com.gy.githubclient.feature.search.domain.model.SearchType
import com.gy.githubclient.feature.search.domain.repository.SearchRepository
import com.gy.githubclient.feature.search.domain.usecase.SearchUseCase
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 搜索领域层纯逻辑测试。 */
class SearchDomainTest {
    @Test
    fun blankQueryIsRejectedBeforeRepositoryCall() =
        runTest {
            var called = false
            val repo =
                object : SearchRepository {
                    override suspend fun search(query: SearchQuery): NetworkResult<SearchResult<SearchHit>> {
                        called = true
                        return emptyResult(query)
                    }
                }
            val result = SearchUseCase(repo)(SearchQuery(term = "   "))

            assertTrue(result is NetworkResult.Failure)
            assertFalse("空白关键字不应触发网络请求", called)
        }

    @Test
    fun validQueryDelegatesToRepository() =
        runTest {
            val repo =
                object : SearchRepository {
                    override suspend fun search(query: SearchQuery) = emptyResult(query)
                }
            val query = SearchQuery(term = "kotlin", type = SearchType.CODE)
            val result = SearchUseCase(repo)(query)

            assertTrue(result is NetworkResult.Success)
            assertEquals(query, (result as NetworkResult.Success).data.query)
        }

    @Test
    fun hasNextPageReflectsTotalCount() {
        val query = SearchQuery(term = "x", page = 1, perPage = 20)
        val full =
            SearchResult(totalCount = 100, incompleteResults = false, items = emptyList<String>(), query = query)
        val last = full.copy(totalCount = 20)

        assertTrue(full.hasNextPage)
        assertFalse(last.hasNextPage)
        assertEquals(2, query.nextPage().page)
    }

    private fun emptyResult(query: SearchQuery): NetworkResult<SearchResult<SearchHit>> =
        NetworkResult.Success(
            SearchResult(totalCount = 0, incompleteResults = false, items = emptyList(), query = query),
        )
}
