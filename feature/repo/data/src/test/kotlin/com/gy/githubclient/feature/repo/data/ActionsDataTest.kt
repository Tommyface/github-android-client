/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.domain.network.ApiChannel
import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.data.remote.GitHubRepoApi
import com.gy.githubclient.feature.repo.data.remote.WorkflowRunDto
import com.gy.githubclient.feature.repo.data.remote.WorkflowRunsResponseDto
import com.gy.githubclient.feature.repo.data.remote.toPage
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

/** v0.14.0 A3/A4 数据层回归：Actions DTO 映射、runs 端点、文件历史端点。 */
class ActionsDataTest {
    @Test
    fun dtoMapsToPageWithHasMore() {
        val page =
            WorkflowRunsResponseDto(
                totalCount = 2,
                workflowRuns =
                    listOf(
                        WorkflowRunDto(id = 1L, name = "CI", status = "completed", conclusion = "success", headSha = "abcdef1234567890"),
                    ),
            ).toPage(perPage = 1)
        assertEquals(1, page.runs.size)
        assertTrue(page.hasMore)
        assertEquals("abcdef1", page.runs.first().shortSha)
    }

    @Test
    fun listWorkflowRunsHitsActionsEndpoint() =
        runTest {
            var captured = ""
            val engine =
                MockEngine { request ->
                    captured = request.url.toString()
                    respond(
                        """{"total_count":1,"workflow_runs":[{"id":1,"name":"CI","event":"push","status":"completed","conclusion":"success","head_branch":"main","head_sha":"abc","run_number":5,"html_url":"u"}]}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val api = buildApi(engine)
            val result = api.listWorkflowRuns("o", "r", 1, 30)
            assertTrue(result is NetworkResult.Success)
            assertEquals(
                "CI",
                (result as NetworkResult.Success)
                    .data.runs
                    .first()
                    .name,
            )
            assertTrue(captured.contains("/repos/o/r/actions/runs"))
            assertTrue(captured.contains("page=1"))
        }

    @Test
    fun listFileCommitsHitsCommitsPathEndpoint() =
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
            val result = api.listFileCommits("o", "r", "a/b.txt", "main")
            assertTrue(result is NetworkResult.Success)
            assertEquals("m", (result as NetworkResult.Success).data.first().title)
            assertTrue(captured.contains("/repos/o/r/commits"))
            // Ktor encodeURLPath 保留路径分隔符 '/'，仅编码空格/中文等；query 中斜杠合法。
            assertTrue(captured.contains("path=a/b.txt"))
            assertTrue(captured.contains("ref=main"))
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
