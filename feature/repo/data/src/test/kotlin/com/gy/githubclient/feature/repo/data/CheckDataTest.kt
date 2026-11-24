/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.domain.network.ApiChannel
import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.data.remote.CheckRunsResponseDto
import com.gy.githubclient.feature.repo.data.remote.CombinedStatusDto
import com.gy.githubclient.feature.repo.data.remote.GitHubRepoApi
import com.gy.githubclient.feature.repo.data.remote.buildCheckOverview
import com.gy.githubclient.feature.repo.data.remote.toDomainRuns
import com.gy.githubclient.feature.repo.data.remote.toDomainStatuses
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

/** v0.13.0 A2 数据层回归：CI DTO 映射与 REST 端点路径。 */
class CheckDataTest {
    @Test
    fun dtoMapsToDomainRunsAndStatuses() {
        val runs =
            CheckRunsResponseDto(
                totalCount = 1,
                checkRuns =
                    listOf(
                        com.gy.githubclient.feature.repo.data.remote.CheckRunDto(
                            name = "build",
                            status = "completed",
                            conclusion = "success",
                            htmlUrl = "u",
                        ),
                    ),
            ).toDomainRuns()
        assertEquals("build", runs.first().name)
        assertEquals("u", runs.first().htmlUrl)

        val statuses =
            CombinedStatusDto(
                state = "failure",
                statuses =
                    listOf(
                        com.gy.githubclient.feature.repo.data.remote
                            .StatusItemDto("lint", "failure", "url"),
                    ),
            ).toDomainStatuses()
        assertEquals("lint", statuses.first().context)

        val overview = buildCheckOverview("sha", runs, statuses)
        assertEquals("sha", overview.sha)
        assertEquals(2, overview.runs.size + overview.statuses.size)
    }

    @Test
    fun checkRunsApiHitsCheckRunsEndpoint() =
        runTest {
            var captured = ""
            val engine =
                MockEngine { request ->
                    captured = request.url.toString()
                    respond(
                        """{"total_count":1,"check_runs":[{"name":"build","status":"completed","conclusion":"success","html_url":"u"}]}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val api = buildApi(engine)
            val result = api.listCheckRuns("o", "r", "ref")
            assertTrue(result is NetworkResult.Success)
            assertEquals("build", (result as NetworkResult.Success).data.first().name)
            assertTrue(captured.contains("/repos/o/r/commits/ref/check-runs"))
        }

    @Test
    fun combinedStatusApiHitsStatusEndpoint() =
        runTest {
            var captured = ""
            val engine =
                MockEngine { request ->
                    captured = request.url.toString()
                    respond(
                        """{"state":"success","statuses":[{"context":"lint","state":"success","target_url":"url"}]}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val api = buildApi(engine)
            val result = api.getCombinedStatus("o", "r", "ref")
            assertTrue(result is NetworkResult.Success)
            assertEquals("lint", (result as NetworkResult.Success).data.first().context)
            assertTrue(captured.endsWith("/repos/o/r/commits/ref/status"))
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
