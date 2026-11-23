/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.search.data.remote

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.search.domain.model.SearchHit
import com.gy.githubclient.feature.search.domain.model.SearchQuery
import com.gy.githubclient.feature.search.domain.model.SearchResult
import com.gy.githubclient.feature.search.domain.model.SearchType
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse

/**
 * GitHub Search REST API。
 * 所有认证、限流退避由统一 [GitHubApiClient] 处理；Search 有独立限流额度。
 * 按 [SearchType] 分派到不同的 DTO 反序列化目标，统一产出 [SearchResult]。
 */
class GitHubSearchApi(
    private val client: GitHubApiClient,
) {
    suspend fun search(query: SearchQuery): NetworkResult<SearchResult<SearchHit>> =
        client.request(
            path = searchPath(query),
            mapper = { response: HttpResponse -> parse(response, query) },
        )

    private suspend fun parse(
        response: HttpResponse,
        query: SearchQuery,
    ): SearchResult<SearchHit> =
        when (query.type) {
            SearchType.REPOSITORY -> {
                val dto = response.body<SearchResponseDto<RepoSearchItemDto>>()
                buildResult(dto, query) { it.toHit() }
            }

            SearchType.CODE -> {
                val dto = response.body<SearchResponseDto<CodeSearchItemDto>>()
                buildResult(dto, query) { it.toHit() }
            }

            SearchType.ISSUE -> {
                val dto = response.body<SearchResponseDto<IssueSearchItemDto>>()
                buildResult(dto, query) { it.toHit() }
            }

            SearchType.USER -> {
                val dto = response.body<SearchResponseDto<UserSearchItemDto>>()
                buildResult(dto, query) { it.toHit() }
            }

            SearchType.COMMIT -> {
                val dto = response.body<SearchResponseDto<CommitSearchItemDto>>()
                buildResult(dto, query) { it.toHit() }
            }
        }

    private fun <T, R : SearchHit> buildResult(
        dto: SearchResponseDto<T>,
        query: SearchQuery,
        map: (T) -> R,
    ): SearchResult<SearchHit> =
        SearchResult(
            totalCount = dto.totalCount,
            incompleteResults = dto.incompleteResults,
            items = dto.items.map(map),
            query = query,
        )

    private fun searchPath(query: SearchQuery): String {
        val term = encode(query.term.trim())
        val base = "/search/${query.type.endpoint}?q=$term&page=${query.page}&per_page=${query.perPage}"
        val sortPart = query.sort?.let { "&sort=$it" }.orEmpty()
        val orderPart = "&order=${query.order.value}"
        return base + sortPart + orderPart
    }

    /** 轻量 URL 编码：保留字母数字与常见符号，其余转百分号编码。 */
    private fun encode(value: String): String =
        value
            .map { char ->
                when (char) {
                    in 'a'..'z', in 'A'..'Z', in '0'..'9', '-', '_', '.', '~' -> char.toString()
                    ' ' -> "+"
                    else ->
                        char.toString().toByteArray().joinToString("") { byte ->
                            "%" + (byte.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
                        }
                }
            }.joinToString("")
}
