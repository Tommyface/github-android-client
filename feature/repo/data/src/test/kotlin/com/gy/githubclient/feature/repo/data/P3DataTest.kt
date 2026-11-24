/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.domain.network.ApiChannel
import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.data.remote.ForkRepoDto
import com.gy.githubclient.feature.repo.data.remote.GitHubRepoApi
import com.gy.githubclient.feature.repo.data.remote.LabelDto
import com.gy.githubclient.feature.repo.data.remote.MilestoneDto
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

/** P3 数据层（v0.17.0）：DTO 映射与三个端点路径。 */
class P3DataTest {
    @Test
    fun labelDtoMapsToDomain() {
        val domain = LabelDto(name = "bug", color = "d73a4a", description = "something wrong").toDomain()
        assertEquals("bug", domain.name)
        assertEquals("d73a4a", domain.color)
        assertEquals("something wrong", domain.description)
    }

    @Test
    fun milestoneDtoMapsToDomain() {
        val dto =
            MilestoneDto(
                number = 7,
                title = "v2",
                state = "closed",
                openIssues = 1,
                closedIssues = 9,
                htmlUrl = "https://github.com/o/r/milestone/7",
            )
        val domain = dto.toDomain()
        assertEquals(7, domain.number)
        assertEquals(9, domain.closedIssues)
        assertEquals("closed", domain.state)
    }

    @Test
    fun forkDtoMapsToDomain() {
        val dto =
            ForkRepoDto(
                fullName = "someone/r",
                owner =
                    com.gy.githubclient.feature.repo.data.remote
                        .OwnerDto(login = "someone", avatarUrl = "https://avatar"),
                stargazersCount = 12,
                htmlUrl = "https://github.com/someone/r",
            )
        val domain = dto.toDomain()
        assertEquals("someone/r", domain.fullName)
        assertEquals("someone", domain.ownerLogin)
        assertEquals(12, domain.stargazersCount)
    }

    @Test
    fun apiHitsLabelsEndpoint() =
        runTest {
            var captured = ""
            val engine =
                MockEngine { request ->
                    captured = request.url.toString()
                    respond(
                        """[{"name":"bug","color":"d73a4a","description":"x"}]""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val result = buildApi(engine).listLabels("o", "r")

            assertTrue(result is NetworkResult.Success)
            assertTrue(captured.contains("/repos/o/r/labels"))
            assertEquals("bug", (result as NetworkResult.Success).data.first().name)
        }

    @Test
    fun apiHitsMilestonesEndpoint() =
        runTest {
            var captured = ""
            val engine =
                MockEngine { request ->
                    captured = request.url.toString()
                    respond(
                        """[{"number":7,"title":"v2","state":"open","open_issues":1,"closed_issues":9,"html_url":"u"}]""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val result = buildApi(engine).listMilestones("o", "r")

            assertTrue(result is NetworkResult.Success)
            assertTrue(captured.contains("/repos/o/r/milestones"))
            assertTrue(captured.contains("state=all"))
            assertEquals(9, (result as NetworkResult.Success).data.first().closedIssues)
        }

    @Test
    fun apiHitsForksEndpoint() =
        runTest {
            var captured = ""
            val engine =
                MockEngine { request ->
                    captured = request.url.toString()
                    respond(
                        """[{"full_name":"someone/r","owner":{"login":"someone"},"stargazers_count":12,"html_url":"u"}]""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val result = buildApi(engine).listForks("o", "r")

            assertTrue(result is NetworkResult.Success)
            assertTrue(captured.contains("/repos/o/r/forks"))
            assertEquals("someone", (result as NetworkResult.Success).data.first().ownerLogin)
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
