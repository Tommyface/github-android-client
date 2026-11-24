/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.data

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.domain.network.ApiChannel
import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.profile.data.local.ProfileDao
import com.gy.githubclient.feature.profile.data.local.ProfileEntity
import com.gy.githubclient.feature.profile.data.local.toDomain
import com.gy.githubclient.feature.profile.data.local.toEntity
import com.gy.githubclient.feature.profile.data.remote.GitHubUserApi
import com.gy.githubclient.feature.profile.data.remote.NotificationDto
import com.gy.githubclient.feature.profile.data.remote.UserDto
import com.gy.githubclient.feature.profile.data.remote.toDomain
import com.gy.githubclient.feature.profile.data.remote.toThreadId
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

/** 个人资料数据层：DTO 映射、Entity 往返、API 集成与缓存回填。 */
class ProfileDataTest {
    private val sampleDto =
        UserDto(
            id = 7L,
            login = "glorywork",
            name = "Gy",
            avatarUrl = "https://avatars.example/gy",
            bio = "工程师",
            publicRepos = 42,
            followers = 100,
            following = 8,
        )

    @Test
    fun dtoMapsToDomainWithAllFields() {
        val domain = sampleDto.toDomain()

        assertEquals(7L, domain.id)
        assertEquals("glorywork", domain.login)
        assertEquals("Gy", domain.name)
        assertEquals("工程师", domain.bio)
        assertEquals(42, domain.publicRepos)
        assertEquals(100, domain.followers)
    }

    @Test
    fun entityRoundTripPreservesFields() {
        val domain = sampleDto.toDomain()
        val restored = domain.toEntity().toDomain()

        assertEquals(domain.id, restored.id)
        assertEquals(domain.login, restored.login)
        assertEquals(domain.publicRepos, restored.publicRepos)
        assertEquals(domain.followers, restored.followers)
    }

    @Test
    fun apiParsesCurrentUser() =
        runTest {
            val json =
                """
                {"id":1,"login":"octocat","name":"The Octocat","public_repos":8,"followers":999}
                """.trimIndent()
            val engine =
                MockEngine {
                    respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val api = GitHubUserApi(buildClient(engine))

            val result = api.getCurrentUser()

            assertTrue(result is NetworkResult.Success)
            val profile = (result as NetworkResult.Success).data
            assertEquals("octocat", profile.login)
            assertEquals("The Octocat", profile.name)
            assertEquals(999, profile.followers)
        }

    @Test
    fun refreshUpsertsCacheOnSuccess() =
        runTest {
            val dao = FakeProfileDao()
            val engine =
                MockEngine {
                    respond(
                        """{"id":3,"login":"cached"}""",
                        HttpStatusCode.OK,
                        headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
            val repo = ProfileRepositoryImpl(dao, GitHubUserApi(buildClient(engine)))

            val result = repo.refreshProfile()

            assertTrue(result is NetworkResult.Success)
            assertEquals(1, dao.upsertCalls)
            assertEquals("cached", dao.lastEntity?.login)
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

    private class FakeProfileDao : ProfileDao {
        var upsertCalls = 0
            private set

        var lastEntity: ProfileEntity? = null
            private set

        override fun observeProfile(): Flow<ProfileEntity?> = flowOf(null)

        override suspend fun upsert(entity: ProfileEntity) {
            upsertCalls++
            lastEntity = entity
        }

        override suspend fun clear() = Unit
    }
}

/** v0.6.0 M7 回归：通知 DTO 解析（id 十进制字符串 → threadId、主题字段映射）。 */
class NotificationDtoTest {
    @Test
    fun notificationDtoMapsToDomain() {
        val dto =
            NotificationDto(
                id = "1234567890",
                unread = true,
                reason = "comment",
                subject =
                    NotificationDto.SubjectDto(
                        title = "修复收藏 403",
                        type = "PullRequest",
                        url = "https://api.github.com/repos/o/r/pulls/7",
                    ),
                repository =
                    NotificationDto.RepoDto(
                        fullName = "Tommyface/github-android-client",
                        htmlUrl = "https://github.com/Tommyface/github-android-client",
                    ),
                updatedAt = "2026-11-24T10:00:00Z",
            )
        val domain = dto.toDomain()
        assertEquals(1234567890L, domain.id)
        assertEquals(1234567890L, domain.threadId)
        assertEquals("comment", domain.reason)
        assertEquals("修复收藏 403", domain.subject.title)
        assertEquals("PullRequest", domain.subject.type)
        assertEquals("Tommyface/github-android-client", domain.repoFullName)
    }

    @Test
    fun invalidIdFallsBackToZero() {
        assertEquals(0L, NotificationDto(id = "not-a-number").toThreadId())
    }
}
