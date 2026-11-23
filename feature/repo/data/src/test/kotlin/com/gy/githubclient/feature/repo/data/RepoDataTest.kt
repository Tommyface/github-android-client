/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.domain.network.ApiChannel
import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.core.domain.network.Conditional
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.data.local.RepoDao
import com.gy.githubclient.feature.repo.data.local.toDomain
import com.gy.githubclient.feature.repo.data.local.toEntity
import com.gy.githubclient.feature.repo.data.remote.GitHubRepoApi
import com.gy.githubclient.feature.repo.data.remote.OwnerDto
import com.gy.githubclient.feature.repo.data.remote.RepositoryDto
import com.gy.githubclient.feature.repo.data.remote.toDomain
import com.gy.githubclient.feature.repo.domain.model.RepoFilter
import com.gy.githubclient.feature.repo.domain.model.RepoVisibility
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

/** 仓库数据层：DTO 映射、Entity 往返、API 集成、缓存未修改分支。 */
class RepoDataTest {
    private val sampleDto =
        RepositoryDto(
            id = 42L,
            name = "licai-ledger",
            fullName = "glorywork/licai-ledger",
            owner = OwnerDto(login = "glorywork", htmlUrl = "https://github.com/glorywork"),
            description = "理财台账",
            private = false,
            visibility = "public",
            topics = listOf("kotlin", "pwa"),
        )

    @Test
    fun dtoMapsToDomainWithCorrectVisibilityAndTopics() {
        val domain = sampleDto.toDomain()

        assertEquals(42L, domain.id)
        assertEquals("glorywork/licai-ledger", domain.fullName)
        assertEquals(RepoVisibility.PUBLIC, domain.visibility)
        assertEquals(listOf("kotlin", "pwa"), domain.topics)
        assertEquals("glorywork", domain.owner.login)
    }

    @Test
    fun entityRoundTripPreservesFields() {
        val domain = sampleDto.toDomain()
        val entity = domain.toEntity(bucket = "OWNER_UPDATED_DESC", page = 1)
        val restored = entity.toDomain()

        assertEquals(domain.id, restored.id)
        assertEquals(domain.fullName, restored.fullName)
        assertEquals(domain.topics, restored.topics)
        assertEquals(domain.visibility, restored.visibility)
    }

    @Test
    fun apiParsesRepositoryList() =
        runTest {
            val json =
                """
                [{"id":1,"name":"a","full_name":"o/a","owner":{"login":"o"},"private":false}]
                """.trimIndent()
            val engine =
                MockEngine {
                    respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val api = buildApi(engine)

            val result = api.listRepositories(RepoFilter(), etag = null)

            assertTrue(result is NetworkResult.Success)
            val payload = (result as NetworkResult.Success).data
            assertTrue(payload is Conditional.Data)
            assertEquals(
                "o/a",
                (payload as Conditional.Data)
                    .value.items
                    .first()
                    .fullName,
            )
        }

    @Test
    fun notModifiedDoesNotRewriteCache() =
        runTest {
            val dao = FakeRepoDao()
            // 让 API 返回 304 的替身：通过 mock 客户端而非直接调用 api
            val api304 =
                GitHubRepoApi(
                    GitHubApiClient(
                        buildTestClient(MockEngine { respond("", HttpStatusCode.NotModified) }),
                        FakeTokenProvider("t"),
                    ),
                )
            val repo304 = RepoRepositoryImpl(dao, api304)

            val result = repo304.refreshRepositories(RepoFilter(), append = false)

            assertEquals(NetworkResult.Success(0), result)
            assertEquals(0, dao.upsertCalls)
        }

    private fun buildApi(engine: MockEngine): GitHubRepoApi =
        GitHubRepoApi(
            GitHubApiClient(
                buildTestClient(engine),
                FakeTokenProvider("t"),
            ),
        )

    private fun buildTestClient(engine: MockEngine): HttpClient =
        HttpClient(engine) {
            expectSuccess = false
            install(ContentNegotiation) { json() }
        }

    private class FakeTokenProvider(
        private val primary: String?,
    ) : AuthTokenProvider {
        override suspend fun tokenFor(channel: ApiChannel): String? =
            if (channel == ApiChannel.PRIMARY) primary else null

        override suspend fun onUnauthorized(channel: ApiChannel) = Unit
    }

    /** 最小 DAO 替身，记录写入次数。 */
    private class FakeRepoDao : RepoDao {
        var upsertCalls = 0
            private set

        override fun observeByBucket(bucket: String): Flow<List<com.gy.githubclient.feature.repo.data.local.RepoEntity>> =
            flowOf(
                emptyList(),
            )

        override suspend fun getById(id: Long) = null

        override suspend fun findByFullName(fullName: String) = null

        override suspend fun getEtag(bucket: String): String? = null

        override suspend fun upsertAll(items: List<com.gy.githubclient.feature.repo.data.local.RepoEntity>) {
            upsertCalls++
        }

        override suspend fun setEtag(meta: com.gy.githubclient.feature.repo.data.local.RepoListMetaEntity) = Unit

        override suspend fun replaceBucket(
            bucket: String,
            items: List<com.gy.githubclient.feature.repo.data.local.RepoEntity>,
            etag: String?,
        ) {
            upsertCalls++
        }

        override suspend fun appendAll(items: List<com.gy.githubclient.feature.repo.data.local.RepoEntity>) {
            upsertCalls++
        }

        override suspend fun clearBucket(bucket: String) = Unit

        override suspend fun updateStarred(
            id: Long,
            starred: Boolean,
        ) = Unit

        override suspend fun clearAll() = Unit

        override suspend fun clearAllMeta() = Unit
    }
}
