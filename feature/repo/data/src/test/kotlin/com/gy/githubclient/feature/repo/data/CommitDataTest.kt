/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.domain.network.ApiChannel
import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.data.remote.CommitDetailDto
import com.gy.githubclient.feature.repo.data.remote.CommitFileDto
import com.gy.githubclient.feature.repo.data.remote.CommitItemDto
import com.gy.githubclient.feature.repo.data.remote.CommitPayloadDto
import com.gy.githubclient.feature.repo.data.remote.GitAuthorDto
import com.gy.githubclient.feature.repo.data.remote.GitHubRepoApi
import com.gy.githubclient.feature.repo.data.remote.toDetail
import com.gy.githubclient.feature.repo.data.remote.toSummary
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

/** v0.12.0 N10 数据层回归：Commit DTO 映射与 REST 端点路径。 */
class CommitDataTest {
    @Test
    fun dtoMapsToDomainSummaryAndDetail() {
        val item =
            CommitItemDto(
                sha = "abcdef1234567890",
                commit = CommitPayloadDto("修复\n详情", GitAuthorDto("dev", "2026-11-25T00:00:00Z")),
                htmlUrl = "https://github.com/o/r/commit/abcdef1234567890",
            )
        val summary = item.toSummary()
        assertEquals("abcdef1234567890", summary.sha)
        assertEquals("修复", summary.title)
        assertEquals("dev", summary.authorName)

        val detail =
            CommitDetailDto(
                sha = "abcdef1234567890",
                commit = CommitPayloadDto("修复\n详情", GitAuthorDto("dev", "2026-11-25T00:00:00Z")),
                htmlUrl = item.htmlUrl,
                files =
                    listOf(
                        CommitFileDto("a.kt", "modified", 3, 1, "@@ -1 +1 @@\n-old\n+new"),
                        CommitFileDto("b.bin", "added", 0, 0, null),
                    ),
            ).toDetail()
        assertEquals(2, detail.files.size)
        assertEquals(3, detail.totalAdditions)
        assertEquals(1, detail.totalDeletions)
        assertTrue(detail.files[1].isBinary)
        assertEquals("a.kt", detail.files[0].filename)
    }

    @Test
    fun listCommitsApiHitsCommitsEndpoint() =
        runTest {
            var captured = ""
            val engine =
                MockEngine { request ->
                    captured = request.url.toString()
                    respond(
                        """[{"sha":"abc","commit":{"message":"m","author":{"name":"a","date":"d"}},"html_url":"u"}]""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val api = buildApi(engine)
            val result = api.listCommits("o", "r", 2, 30)
            assertTrue(result is NetworkResult.Success)
            val data = (result as NetworkResult.Success).data
            assertEquals("abc", data.first().sha)
            assertTrue(captured.contains("/repos/o/r/commits"))
            assertTrue(captured.contains("page=2"))
            assertTrue(captured.contains("per_page=30"))
        }

    @Test
    fun getCommitDetailApiHitsCommitShaEndpoint() =
        runTest {
            var captured = ""
            val engine =
                MockEngine { request ->
                    captured = request.url.toString()
                    val body =
                        """
                        {"sha":"abc","commit":{"message":"m"},"html_url":"u",
                         "files":[{"filename":"a.kt","status":"modified","additions":1,
                         "deletions":0,"patch":"@@ -1 +1 @@\n+x"}]}
                        """.trimIndent()
                    respond(
                        body,
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val api = buildApi(engine)
            val result = api.getCommitDetail("o", "r", "abc")
            assertTrue(result is NetworkResult.Success)
            val data = (result as NetworkResult.Success).data
            assertEquals("a.kt", data.files.first().filename)
            assertTrue(captured.endsWith("/repos/o/r/commits/abc"))
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
