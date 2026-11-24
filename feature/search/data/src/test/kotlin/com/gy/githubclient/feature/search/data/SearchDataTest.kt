/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.search.data

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.domain.network.ApiChannel
import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.search.data.remote.GitHubSearchApi
import com.gy.githubclient.feature.search.data.remote.OwnerMiniDto
import com.gy.githubclient.feature.search.data.remote.RepoSearchItemDto
import com.gy.githubclient.feature.search.data.remote.toHit
import com.gy.githubclient.feature.search.domain.model.CodeHit
import com.gy.githubclient.feature.search.domain.model.RepoHit
import com.gy.githubclient.feature.search.domain.model.SearchQuery
import com.gy.githubclient.feature.search.domain.model.SearchType
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 搜索数据层：DTO 映射、API 分派解析、限流错误透传。 */
class SearchDataTest {
    @Test
    fun repoDtoMapsToHit() {
        val dto =
            RepoSearchItemDto(
                id = 7L,
                name = "licai-ledger",
                fullName = "glorywork/licai-ledger",
                owner = OwnerMiniDto(login = "glorywork"),
                stargazersCount = 12,
                language = "Kotlin",
                `private` = true,
                topics = listOf("android", "kotlin"),
            )

        val hit = dto.toHit()

        assertEquals("glorywork/licai-ledger", hit.fullName)
        assertEquals("Kotlin", hit.language)
        assertTrue(hit.isPrivate)
        assertEquals(listOf("android", "kotlin"), hit.topics)
    }

    @Test
    fun apiParsesRepositorySearch() =
        runTest {
            val json =
                """
                {"total_count":1,"incomplete_results":false,"items":[{"id":1,"name":"a","full_name":"o/a","owner":{"login":"o"}}]}
                """.trimIndent()
            val api = buildApi(json)

            val result = api.search(SearchQuery(term = "a", type = SearchType.REPOSITORY))

            assertTrue(result is NetworkResult.Success)
            val data = (result as NetworkResult.Success).data
            assertEquals(1, data.totalCount)
            assertTrue(data.items.first() is RepoHit)
            assertEquals("o/a", (data.items.first() as RepoHit).fullName)
        }

    @Test
    fun apiParsesCodeSearch() =
        runTest {
            val json =
                """
                {"total_count":1,"incomplete_results":false,"items":[{"name":"Main.kt","path":"src/Main.kt","sha":"abc","html_url":"https://github.com/o/r/blob/main/src/Main.kt","repository":{"full_name":"o/r","html_url":"https://github.com/o/r"}}]}
                """.trimIndent()
            val api = buildApi(json)

            val result = api.search(SearchQuery(term = "Main", type = SearchType.CODE))

            assertTrue(result is NetworkResult.Success)
            val hit = (result as NetworkResult.Success).data.items.first()
            assertTrue(hit is CodeHit)
            assertEquals("src/Main.kt", (hit as CodeHit).path)
        }

    @Test
    fun rateLimitIsMappedToFailure() =
        runTest {
            val engine =
                MockEngine {
                    respond(
                        "{}",
                        HttpStatusCode.Forbidden,
                        headersOf("X-RateLimit-Remaining" to listOf("0"), "X-RateLimit-Reset" to listOf("1")),
                    )
                }
            val api =
                GitHubSearchApi(
                    GitHubApiClient(
                        HttpClient(engine) {
                            expectSuccess = false
                            install(ContentNegotiation) { json() }
                        },
                        FakeTokenProvider("t"),
                        maxRetries = 0,
                    ),
                )

            val result = api.search(SearchQuery(term = "x"))

            assertTrue(result is NetworkResult.Failure)
            assertTrue((result as NetworkResult.Failure).error is ApiError.RateLimited)
        }

    private fun buildApi(json: String): GitHubSearchApi =
        GitHubSearchApi(
            GitHubApiClient(
                HttpClient(
                    MockEngine {
                        respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                ) {
                    expectSuccess = false
                    install(ContentNegotiation) { json() }
                },
                FakeTokenProvider("t"),
            ),
        )

    private class FakeTokenProvider(
        private val primary: String?,
    ) : AuthTokenProvider {
        override suspend fun tokenFor(channel: ApiChannel): String? =
            if (channel == ApiChannel.PRIMARY) primary else null

        override suspend fun onUnauthorized(channel: ApiChannel) = Unit
    }
}
