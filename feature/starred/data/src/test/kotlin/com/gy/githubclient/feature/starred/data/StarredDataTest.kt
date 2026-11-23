/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.starred.data

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.domain.network.ApiChannel
import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.domain.model.RepoVisibility
import com.gy.githubclient.feature.starred.data.local.StarredDao
import com.gy.githubclient.feature.starred.data.local.StarredEntity
import com.gy.githubclient.feature.starred.data.local.toDomain
import com.gy.githubclient.feature.starred.data.local.toEntity
import com.gy.githubclient.feature.starred.data.remote.GitHubStarredApi
import com.gy.githubclient.feature.starred.data.remote.StarredOwnerDto
import com.gy.githubclient.feature.starred.data.remote.StarredRepoDto
import com.gy.githubclient.feature.starred.data.remote.toDomain
import com.gy.githubclient.feature.starred.domain.model.StarredFilter
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

/** 星标数据层：DTO 映射、Entity 往返、API 集成、分页与移除星标。 */
class StarredDataTest {
    private val sampleDto =
        StarredRepoDto(
            id = 42L,
            name = "licai-ledger",
            fullName = "glorywork/licai-ledger",
            owner = StarredOwnerDto(login = "glorywork", htmlUrl = "https://github.com/glorywork"),
            description = "理财台账",
            visibility = "public",
            stargazersCount = 12,
        )

    @Test
    fun dtoMapsToDomainAsStarredWithVisibility() {
        val domain = sampleDto.toDomain()

        assertEquals(42L, domain.id)
        assertEquals("glorywork/licai-ledger", domain.fullName)
        assertEquals(RepoVisibility.PUBLIC, domain.visibility)
        assertTrue(domain.isStarred)
        assertEquals(12, domain.stargazersCount)
    }

    @Test
    fun entityRoundTripPreservesFields() {
        val domain = sampleDto.toDomain()
        val restored = domain.toEntity(bucket = "UPDATED", page = 1).toDomain()

        assertEquals(domain.id, restored.id)
        assertEquals(domain.fullName, restored.fullName)
        assertEquals(domain.visibility, restored.visibility)
        assertTrue(restored.isStarred)
    }

    @Test
    fun apiParsesStarredList() =
        runTest {
            val json =
                """
                [{"id":1,"name":"a","full_name":"o/a","owner":{"login":"o"},"private":false}]
                """.trimIndent()
            val engine =
                MockEngine {
                    respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val api = GitHubStarredApi(buildClient(engine))

            val result = api.listStarred(StarredFilter())

            assertTrue(result is NetworkResult.Success)
            assertEquals(
                "o/a",
                (result as NetworkResult.Success)
                    .data
                    .first()
                    .fullName,
            )
        }

    @Test
    fun refreshReportsHasMoreWhenPageIsFull() =
        runTest {
            val json = buildStarredJson(count = 30)
            val engine =
                MockEngine {
                    respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val dao = FakeStarredDao()
            val repo = StarredRepositoryImpl(dao, GitHubStarredApi(buildClient(engine)))

            val result = repo.refreshStarred(StarredFilter(perPage = 30), append = false)

            assertTrue(result is NetworkResult.Success)
            val page = (result as NetworkResult.Success).data
            assertEquals(30, page.loaded)
            assertTrue(page.hasMore)
            assertEquals(1, dao.replaceCalls)
        }

    @Test
    fun removeStarDeletesFromCacheOnSuccess() =
        runTest {
            val engine = MockEngine { respond("", HttpStatusCode.NoContent) }
            val dao = FakeStarredDao()
            val repo = StarredRepositoryImpl(dao, GitHubStarredApi(buildClient(engine)))

            val result = repo.removeStar("glorywork", "licai-ledger")

            assertTrue(result is NetworkResult.Success)
            assertEquals("glorywork/licai-ledger", dao.deletedFullName)
        }

    private fun buildStarredJson(count: Int): String =
        buildString {
            append("[")
            repeat(count) { index ->
                if (index > 0) append(",")
                append("""{"id":$index,"name":"r$index","full_name":"o/r$index","owner":{"login":"o"},"private":false}""")
            }
            append("]")
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

    private class FakeStarredDao : StarredDao {
        var replaceCalls = 0
            private set

        var deletedFullName: String? = null
            private set

        override fun observeAll(): Flow<List<StarredEntity>> = flowOf(emptyList())

        override suspend fun upsertAll(items: List<StarredEntity>) = Unit

        override suspend fun replaceBucket(
            bucket: String,
            items: List<StarredEntity>,
        ) {
            replaceCalls++
        }

        override suspend fun clearBucket(bucket: String) = Unit

        override suspend fun deleteByFullName(fullName: String) {
            deletedFullName = fullName
        }

        override suspend fun clearAll() = Unit
    }
}
