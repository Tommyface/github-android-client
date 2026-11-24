/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.domain.network.ApiChannel
import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.data.remote.ContributorDto
import com.gy.githubclient.feature.repo.data.remote.GitHubRepoApi
import com.gy.githubclient.feature.repo.data.remote.GitTreeResponseDto
import com.gy.githubclient.feature.repo.data.remote.TreeNodeDto
import com.gy.githubclient.feature.repo.data.remote.toLanguageShares
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

/** P2 数据层（v0.16.0）：DTO 映射与三个端点路径。 */
class P2DataTest {
    @Test
    fun gitTreeDtoMapsToDomain() {
        val dto =
            GitTreeResponseDto(
                sha = "abc",
                tree =
                    listOf(
                        TreeNodeDto(path = "src", type = "tree", sha = "t1"),
                        TreeNodeDto(path = "src/Main.kt", type = "blob", sha = "t2", size = 100L),
                    ),
                truncated = false,
            )

        val domain = dto.toDomain()

        assertEquals(2, domain.nodes.size)
        assertEquals(1, domain.fileCount)
        assertEquals(1, domain.dirCount)
        assertEquals(100L, domain.nodes[1].size)
        assertTrue(!domain.isTruncated)
    }

    @Test
    fun contributorDtoMapsToDomain() {
        val dto = ContributorDto(login = "octocat", htmlUrl = "https://github.com/octocat", contributions = 42)
        val domain = dto.toDomain()
        assertEquals("octocat", domain.login)
        assertEquals(42, domain.contributions)
    }

    @Test
    fun languageMapToShares() {
        val shares = mapOf("Kotlin" to 800L, "Shell" to 200L).toLanguageShares()
        assertEquals(2, shares.size)
        assertEquals("Kotlin", shares.first().language)
        assertEquals(80.0, shares.first().percent, 0.01)
    }

    @Test
    fun apiHitsTreeEndpoint() =
        runTest {
            var captured = ""
            val engine =
                MockEngine { request ->
                    captured = request.url.toString()
                    respond(
                        """{"sha":"abc","tree":[{"path":"a.txt","type":"blob","sha":"s","size":10}],"truncated":false}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val api = buildApi(engine)

            val result = api.getTree("o", "r", "main")

            assertTrue(result is NetworkResult.Success)
            assertTrue(captured.contains("/repos/o/r/git/trees/main"))
            assertTrue(captured.contains("recursive=1"))
            assertEquals(1, (result as NetworkResult.Success).data.fileCount)
        }

    @Test
    fun apiHitsLanguagesEndpoint() =
        runTest {
            var captured = ""
            val engine =
                MockEngine { request ->
                    captured = request.url.toString()
                    respond("""{"Kotlin":800,"Shell":200}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val api = buildApi(engine)

            val result = api.getLanguages("o", "r")

            assertTrue(result is NetworkResult.Success)
            assertTrue(captured.contains("/repos/o/r/languages"))
            assertEquals("Kotlin", (result as NetworkResult.Success).data.first().language)
        }

    @Test
    fun apiHitsContributorsEndpoint() =
        runTest {
            var captured = ""
            val engine =
                MockEngine { request ->
                    captured = request.url.toString()
                    respond(
                        """[{"login":"octocat","html_url":"https://github.com/octocat","contributions":42}]""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val api = buildApi(engine)

            val result = api.listContributors("o", "r")

            assertTrue(result is NetworkResult.Success)
            assertTrue(captured.contains("/repos/o/r/contributors"))
            assertEquals(42, (result as NetworkResult.Success).data.first().contributions)
        }

    private fun buildApi(engine: MockEngine): GitHubRepoApi =
        GitHubRepoApi(
            GitHubApiClient(
                HttpClient(engine) {
                    expectSuccess = false
                    install(ContentNegotiation) { json() }
                },
                FakeTokenProvider("t"),
            ),
        )

    private class FakeTokenProvider(
        private val primary: String?,
    ) : AuthTokenProvider {
        override suspend fun tokenFor(channel: ApiChannel): String? = if (channel == ApiChannel.PRIMARY) primary else null

        override suspend fun onUnauthorized(channel: ApiChannel) = Unit
    }
}
