/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.data

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.domain.network.ApiChannel
import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.discover.data.local.DiscoverDao
import com.gy.githubclient.feature.discover.data.local.DiscoverItemEntity
import com.gy.githubclient.feature.discover.data.local.ReleaseJson
import com.gy.githubclient.feature.discover.data.local.toDomain
import com.gy.githubclient.feature.discover.data.local.toEntity
import com.gy.githubclient.feature.discover.data.remote.GitHubReleaseApi
import com.gy.githubclient.feature.discover.data.remote.ReleaseDto
import com.gy.githubclient.feature.discover.data.remote.TagCommitDto
import com.gy.githubclient.feature.discover.data.remote.TagDto
import com.gy.githubclient.feature.discover.data.remote.toDomain
import com.gy.githubclient.feature.discover.domain.model.DiscoverCategory
import com.gy.githubclient.feature.discover.domain.model.DiscoverItem
import com.gy.githubclient.feature.discover.domain.model.FeaturedRepo
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 发现数据层：DTO 映射、JSON 往返、API 解析、镜像/分享、刷新聚合。 */
class DiscoverDataTest {
    private val seed =
        FeaturedRepo("square", "retrofit", DiscoverCategory.DEVELOPER, "类型安全的 HTTP 客户端", listOf("kotlin"))

    private val releaseDto =
        ReleaseDto(
            tagName = "v2.9.0",
            name = "2.9.0",
            publishedAt = "2026-01-01T00:00:00Z",
            htmlUrl = "https://github.com/square/retrofit/releases/tag/v2.9.0",
            assets =
                listOf(
                    com.gy.githubclient.feature.discover.data.remote.ReleaseAssetDto(
                        id = 7L,
                        name = "sample.apk",
                        size = 1024L,
                        browserDownloadUrl = "https://github.com/square/retrofit/releases/download/v2.9.0/sample.apk",
                        contentType = "application/vnd.android.package-archive",
                    ),
                ),
        )

    @Test
    fun dtoMapsToDomain() {
        val domain = releaseDto.toDomain()

        assertEquals("v2.9.0", domain.tagName)
        assertEquals("2.9.0", domain.name)
        assertEquals(1, domain.assets.size)
        assertEquals("sample.apk", domain.assets.first().name)
        assertTrue(
            domain.assets
                .first()
                .downloadUrl
                .contains("releases/download"),
        )
    }

    @Test
    fun digestStripsSha256PrefixAndSurvivesWireRoundTrip() {
        val withDigest =
            releaseDto.copy(
                assets =
                    listOf(
                        com.gy.githubclient.feature.discover.data.remote.ReleaseAssetDto(
                            id = 7L,
                            name = "sample.apk",
                            size = 1024L,
                            browserDownloadUrl = "https://github.com/square/retrofit/releases/download/v2.9.0/sample.apk",
                            digest = "sha256:ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
                        ),
                    ),
            )

        val domain = withDigest.toDomain()
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", domain.assets.first().sha256)

        // ReleaseJson（缓存序列化）往返不丢失摘要。
        val json = ReleaseJson.encode(domain)
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            ReleaseJson
                .decode(json)
                ?.assets
                ?.first()
                ?.sha256,
        )
    }

    @Test
    fun nonSha256DigestIsIgnored() {
        val md5Only =
            releaseDto.copy(
                assets =
                    listOf(
                        com.gy.githubclient.feature.discover.data.remote.ReleaseAssetDto(
                            id = 7L,
                            name = "sample.apk",
                            size = 1024L,
                            browserDownloadUrl = "https://github.com/square/retrofit/releases/download/v2.9.0/sample.apk",
                            digest = "md5:0123456789abcdef",
                        ),
                    ),
            )

        assertNull(
            md5Only
                .toDomain()
                .assets
                .first()
                .sha256,
        )
        assertNull(
            releaseDto
                .toDomain()
                .assets
                .first()
                .sha256,
        )
    }

    @Test
    fun entityRoundTripPreservesRelease() {
        val item = DiscoverItem(seed, releaseDto.toDomain(), "2026-01-01T00:00:00Z")
        val restored = item.toEntity().toDomain()

        assertEquals(seed.fullName, restored.featured.fullName)
        assertEquals(listOf("kotlin"), restored.featured.topics)
        assertNotNull(restored.latestRelease)
        assertEquals("v2.9.0", restored.latestRelease?.tagName)
        assertEquals(1, restored.latestRelease?.assets?.size)
    }

    @Test
    fun releaseJsonRoundTrip() {
        val json = ReleaseJson.encode(releaseDto.toDomain())
        val decoded = ReleaseJson.decode(json)

        assertNotNull(decoded)
        assertEquals("v2.9.0", decoded?.tagName)
        assertEquals("sample.apk", decoded?.assets?.first()?.name)
    }

    @Test
    fun apiParsesLatestRelease() =
        runTest {
            val json =
                """
                {"tag_name":"v3.0.0","name":"3.0","html_url":"https://github.com/o/r/releases/tag/v3.0.0","assets":[]}
                """.trimIndent()
            val engine =
                MockEngine {
                    respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val api = GitHubReleaseApi(GitHubApiClient(buildTestClient(engine), FakeTokenProvider("t")))

            val result = api.latestRelease("o", "r")

            assertTrue(result is NetworkResult.Success)
            assertEquals("v3.0.0", (result as NetworkResult.Success).data.tagName)
        }

    @Test
    fun refreshAggregatesAndFallsBackOnFailure() =
        runTest {
            val failingApi =
                GitHubReleaseApi(
                    GitHubApiClient(
                        buildTestClient(MockEngine { respond("", HttpStatusCode.NotFound) }),
                        FakeTokenProvider("t"),
                    ),
                )
            val repo = DiscoverRepositoryImpl(FakeDao(), failingApi)

            val result = repo.refreshReleases()

            // 全部 Release 失败 → 返回网络错误，但种子已落 DAO。
            assertTrue(result is NetworkResult.Failure)
        }

    @Test
    fun resolveLinkAndBuildShareText() {
        val repo = DiscoverRepositoryImpl(FakeDao(), buildApi(MockEngine { respond("[]") }))
        val url = "https://github.com/square/retrofit/releases/download/v2.9.0/a.apk"

        val link = repo.resolveDownloadLink(url)
        assertTrue(link.isAccelerated)
        assertEquals(url, link.originalUrl)
        assertNotNull(link.acceleratedUrl)

        val text = repo.buildShareText(DiscoverItem(seed, releaseDto.toDomain(), null))
        assertTrue(text.contains("githubclient://repo/square/retrofit"))
        assertTrue(text.contains("v2.9.0"))
    }

    @Test
    fun tagDtoMapsToDomain() {
        val domain = TagDto("v8.1.1", TagCommitDto("4f638f48abcdef")).toDomain()
        assertEquals("v8.1.1", domain.name)
        assertEquals("4f638f48abcdef", domain.commitSha)
    }

    @Test
    fun apiParsesReleaseListWithPaginationPath() =
        runTest {
            val json =
                """
                [{"tag_name":"v2.9.0","name":"2.9","html_url":"https://github.com/o/r/releases/tag/v2.9.0","assets":[]},
                 {"tag_name":"v2.8.0","name":"2.8","html_url":"https://github.com/o/r/releases/tag/v2.8.0","prerelease":true,"assets":[]}]
                """.trimIndent()
            var requestedPath = ""
            val engine =
                MockEngine { request ->
                    requestedPath = request.url.encodedPathAndQuery
                    respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val api = GitHubReleaseApi(GitHubApiClient(buildTestClient(engine), FakeTokenProvider("t")))

            val result = api.listReleases("o", "r", page = 2, perPage = 10)

            assertTrue(result is NetworkResult.Success)
            assertEquals(2, (result as NetworkResult.Success).data.size)
            assertTrue(requestedPath.contains("/repos/o/r/releases"))
            assertTrue(requestedPath.contains("per_page=10"))
            assertTrue(requestedPath.contains("page=2"))
        }

    @Test
    fun apiParsesTagList() =
        runTest {
            val json =
                """
                [{"name":"v8.1.1","commit":{"sha":"4f638f48abcdef"}}]
                """.trimIndent()
            val engine =
                MockEngine {
                    respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val api = GitHubReleaseApi(GitHubApiClient(buildTestClient(engine), FakeTokenProvider("t")))

            val result = api.listTags("o", "r", perPage = 5)

            assertTrue(result is NetworkResult.Success)
            assertEquals("v8.1.1", (result as NetworkResult.Success).data.first().name)
        }

    @Test
    fun repositoryFiltersDraftAndPrerelease() =
        runTest {
            val json =
                """
                [{"tag_name":"stable","name":"stable","html_url":"u","assets":[]},
                 {"tag_name":"pre","name":"pre","html_url":"u","prerelease":true,"assets":[]},
                 {"tag_name":"draft","name":"draft","html_url":"u","draft":true,"assets":[]}]
                """.trimIndent()
            val api =
                buildApi(
                    MockEngine {
                        respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                    },
                )
            val repo = DiscoverRepositoryImpl(FakeDao(), api)

            val stableOnly = repo.listReleases("o", "r")
            assertTrue(stableOnly is NetworkResult.Success)
            assertEquals(listOf("stable"), (stableOnly as NetworkResult.Success).data.map { it.tagName })

            val withPre = repo.listReleases("o", "r", includePrerelease = true)
            assertEquals(
                setOf("stable", "pre"),
                (withPre as NetworkResult.Success).data.map { it.tagName }.toSet(),
            )
        }

    @Test
    fun listReleasesTranslatesNotFound() =
        runTest {
            val api =
                buildApi(
                    MockEngine {
                        respond("", HttpStatusCode.NotFound)
                    },
                )
            val result = api.listReleases("o", "missing", page = 1, perPage = 30)
            assertTrue(result is NetworkResult.Failure)
        }

    private fun buildApi(engine: MockEngine): GitHubReleaseApi =
        GitHubReleaseApi(GitHubApiClient(buildTestClient(engine), FakeTokenProvider("t")))

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

    private class FakeDao : DiscoverDao {
        var upserted = 0
            private set

        override fun observeAll(): Flow<List<DiscoverItemEntity>> = flowOf(emptyList())

        override suspend fun upsertAll(items: List<DiscoverItemEntity>) {
            upserted += items.size
        }

        override suspend fun clearAll() = Unit
    }
}
