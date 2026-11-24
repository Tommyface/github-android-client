/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.data

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.domain.network.ApiChannel
import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.profile.data.remote.GistDto
import com.gy.githubclient.feature.profile.data.remote.GistFileDto
import com.gy.githubclient.feature.profile.data.remote.GistOwnerDto
import com.gy.githubclient.feature.profile.data.remote.GitHubUserApi
import com.gy.githubclient.feature.profile.data.remote.OrgDto
import com.gy.githubclient.feature.profile.data.remote.OrgMemberDto
import com.gy.githubclient.feature.profile.data.remote.toDomain
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 组织与 Gist 数据层（v0.15.0 A5/A6）：DTO 映射与 API 端点路径。 */
class OrgGistDataTest {
    @Test
    fun orgDtoMapsToDomain() {
        val dto =
            OrgDto(
                login = "github",
                name = "GitHub",
                description = "Where the world builds software",
                htmlUrl = "https://github.com/github",
                publicRepos = 288,
                followers = 100,
                location = "San Francisco",
                createdAt = "2011-01-01T00:00:00Z",
            )

        val domain = dto.toDomain()

        assertEquals("github", domain.login)
        assertEquals("GitHub", domain.name)
        assertEquals(288, domain.publicRepos)
        assertEquals("San Francisco", domain.location)
    }

    @Test
    fun orgMemberDtoMapsToDomain() {
        val dto = OrgMemberDto(login = "octocat", htmlUrl = "https://github.com/octocat")
        val domain = dto.toDomain()
        assertEquals("octocat", domain.login)
        assertEquals("https://github.com/octocat", domain.htmlUrl)
    }

    @Test
    fun gistDtoMapsFilesToDomain() {
        val dto =
            GistDto(
                id = "abc",
                description = "demo",
                htmlUrl = "https://gist.github.com/abc",
                public = true,
                owner = GistOwnerDto(login = "octocat"),
                files =
                    mapOf(
                        "main.kt" to
                            GistFileDto(
                                filename = "main.kt",
                                language = "Kotlin",
                                type = "text/plain",
                                size = 10,
                                content = "fun main()",
                            ),
                    ),
            )

        val domain = dto.toDomain()

        assertEquals("abc", domain.id)
        assertEquals("octocat", domain.ownerLogin)
        assertTrue(domain.isPublic)
        assertEquals(1, domain.files.size)
        assertEquals("fun main()", domain.primaryFile?.content)
        assertEquals("Kotlin", domain.primaryLanguage)
    }

    @Test
    fun apiHitsOrgAndMembersEndpoints() =
        runTest {
            var captured = ""
            val engine =
                MockEngine { request ->
                    captured = request.url.toString()
                    respond(
                        """{"login":"github","public_repos":288}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val api = GitHubUserApi(buildClient(engine))

            val result = api.getOrg("github")

            assertTrue(result is NetworkResult.Success)
            assertTrue(captured.contains("/orgs/github"))
        }

    @Test
    fun apiHitsGistsEndpoint() =
        runTest {
            var captured = ""
            val engine =
                MockEngine { request ->
                    captured = request.url.toString()
                    respond(
                        """[{"id":"abc","description":"demo","public":true,"files":{"a.txt":{"filename":"a.txt","language":"Text"}}}]""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val api = GitHubUserApi(buildClient(engine))

            val result = api.listUserGists("octocat")

            assertTrue(result is NetworkResult.Success)
            assertTrue(captured.contains("/users/octocat/gists"))
            val list = (result as NetworkResult.Success).data
            assertEquals("abc", list.first().id)
            assertEquals("Text", list.first().primaryLanguage)
        }

    @Test
    fun apiHitsGistDetailEndpoint() =
        runTest {
            var captured = ""
            val engine =
                MockEngine { request ->
                    captured = request.url.toString()
                    respond(
                        """{"id":"abc","files":{"a.txt":{"filename":"a.txt","content":"hello"}}}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val api = GitHubUserApi(buildClient(engine))

            val result = api.getGist("abc")

            assertTrue(result is NetworkResult.Success)
            assertTrue(captured.contains("/gists/abc"))
            assertEquals("hello", (result as NetworkResult.Success).data.primaryFile?.content)
        }

    @Test
    fun repositoryForwardsOrgAndGistCalls() =
        runTest {
            val engine =
                MockEngine { request ->
                    val body =
                        when {
                            request.url.toString().contains("/orgs/github/members") -> """[{"login":"octocat"}]"""
                            request.url.toString().contains("/orgs/github") -> """{"login":"github"}"""
                            request.url.toString().contains("/users/octocat/gists") -> """[]"""
                            else -> """{"id":"abc","files":{}}"""
                        }
                    respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val repo = ProfileRepositoryImpl(FakeDao(), GitHubUserApi(buildClient(engine)))

            assertTrue(repo.getOrg("github") is NetworkResult.Success)
            assertEquals(1, (repo.listOrgMembers("github") as NetworkResult.Success).data.size)
            assertTrue(repo.listUserGists("octocat") is NetworkResult.Success)
            assertTrue(repo.getGist("abc") is NetworkResult.Success)
        }

    private fun buildClient(engine: MockEngine): GitHubApiClient =
        GitHubApiClient(
            HttpClient(engine) {
                expectSuccess = false
                install(ContentNegotiation) { json() }
            },
            FakeTokenProvider("t"),
        )

    private class FakeTokenProvider(
        private val primary: String?,
    ) : AuthTokenProvider {
        override suspend fun tokenFor(channel: ApiChannel): String? =
            if (channel == ApiChannel.PRIMARY) primary else null

        override suspend fun onUnauthorized(channel: ApiChannel) = Unit
    }

    private class FakeDao : com.gy.githubclient.feature.profile.data.local.ProfileDao {
        override fun observeProfile(): Flow<com.gy.githubclient.feature.profile.data.local.ProfileEntity?> = flowOf(null)

        override suspend fun upsert(entity: com.gy.githubclient.feature.profile.data.local.ProfileEntity) = Unit

        override suspend fun clear() = Unit
    }
}
