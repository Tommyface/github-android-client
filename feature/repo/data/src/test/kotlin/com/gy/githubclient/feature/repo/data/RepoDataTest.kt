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
import com.gy.githubclient.core.mutation.MutationResult
import com.gy.githubclient.feature.repo.data.local.RepoDao
import com.gy.githubclient.feature.repo.data.local.toDomain
import com.gy.githubclient.feature.repo.data.local.toEntity
import com.gy.githubclient.feature.repo.data.remote.ContentEntryDto
import com.gy.githubclient.feature.repo.data.remote.GitHubRepoApi
import com.gy.githubclient.feature.repo.data.remote.IssueCommentDto
import com.gy.githubclient.feature.repo.data.remote.IssueDetailDto
import com.gy.githubclient.feature.repo.data.remote.IssueItemDto
import com.gy.githubclient.feature.repo.data.remote.OwnerDto
import com.gy.githubclient.feature.repo.data.remote.RepositoryDto
import com.gy.githubclient.feature.repo.data.remote.StarHistoryWeekDto
import com.gy.githubclient.feature.repo.data.remote.toDomain
import com.gy.githubclient.feature.repo.data.remote.toFileContent
import com.gy.githubclient.feature.repo.data.remote.toFileEntry
import com.gy.githubclient.feature.repo.data.remote.toStarHistory
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

    @Test
    fun starStatusReturnsTrueOn204() =
        runTest {
            val api = buildApi(MockEngine { respond("", HttpStatusCode.NoContent) })
            val result = api.starStatus("o", "a")
            assertTrue(result is NetworkResult.Success)
            assertEquals(true, (result as NetworkResult.Success).data)
        }

    @Test
    fun starStatus404TranslatesToFalseInRepository() =
        runTest {
            val dao = FakeRepoDao()
            val repo =
                RepoRepositoryImpl(
                    dao,
                    GitHubRepoApi(
                        GitHubApiClient(
                            buildTestClient(MockEngine { respond("", HttpStatusCode.NotFound) }),
                            FakeTokenProvider("t"),
                        ),
                    ),
                )
            val result = repo.isStarred("o", "a")
            assertTrue(result is NetworkResult.Success)
            assertEquals(false, (result as NetworkResult.Success).data)
        }

    @Test
    fun forkParses202RepositoryResponse() =
        runTest {
            val json =
                """
                {"id":99,"name":"a","full_name":"me/a","owner":{"login":"me"},"private":false,"fork":true}
                """.trimIndent()
            val engine = MockEngine { respond(json, HttpStatusCode.Accepted, headersOf(HttpHeaders.ContentType, "application/json")) }
            val api = buildApi(engine)

            val result = api.fork("o", "a")

            assertTrue(result is NetworkResult.Success)
            val forked = (result as NetworkResult.Success).data
            assertEquals("me/a", forked.fullName)
            assertTrue(forked.isFork)
            assertEquals(
                "/repos/o/a/forks",
                engine.requestHistory
                    .first()
                    .url.encodedPath,
            )
        }

    @Test
    fun starHistoryDtoAccumulatesNewestFirstWeeklyDeltas() {
        // 官方语义：最新周在前、total 为周增量。DTO→领域需反转并累加。
        val history =
            listOf(
                StarHistoryWeekDto(week = 300L, total = 5, days = listOf(1, 2, 0, 0, 1, 1, 0)),
                StarHistoryWeekDto(week = 200L, total = 2),
                StarHistoryWeekDto(week = 100L, total = 3),
            ).toStarHistory()
        assertEquals(listOf(100L, 200L, 300L), history.points.map { it.weekStartEpoch })
        assertEquals(listOf(3, 5, 10), history.points.map { it.cumulativeStars })
    }

    @Test
    fun starHistoryApiHitsHistoryEndpoint() =
        runTest {
            val json =
                """
                [{"week":100,"total":3,"days":[0,0,0,1,1,1,0]}]
                """.trimIndent()
            val engine =
                MockEngine {
                    respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val api = buildApi(engine)

            val result = api.getStarHistory("o", "a")

            assertTrue(result is NetworkResult.Success)
            assertEquals(3, (result as NetworkResult.Success).data.currentTotal)
            assertEquals(
                "/repos/o/a/stargazers/history",
                engine.requestHistory
                    .first()
                    .url.encodedPath,
            )
        }

    @Test
    fun starHistory404TranslatesToEmptyInRepository() =
        runTest {
            val repo =
                RepoRepositoryImpl(
                    FakeRepoDao(),
                    GitHubRepoApi(
                        GitHubApiClient(
                            buildTestClient(MockEngine { respond("", HttpStatusCode.NotFound) }),
                            FakeTokenProvider("t"),
                        ),
                    ),
                )
            val result = repo.getStarHistory("o", "a")
            assertTrue(result is NetworkResult.Success)
            assertTrue((result as NetworkResult.Success).data.points.isEmpty())
        }

    @Test
    fun issueDtoDistinguishesPrByPullRequestField() {
        val issue = IssueItemDto(number = 1, title = "bug", user = OwnerDto(login = "alice"))
        val pr =
            IssueItemDto(
                number = 2,
                title = "feature",
                user = OwnerDto(login = "bob"),
                pullRequest = kotlinx.serialization.json.JsonObject(emptyMap()),
            )
        assertEquals(false, issue.toDomain().isPullRequest)
        assertEquals(true, pr.toDomain().isPullRequest)
        assertEquals("alice", issue.toDomain().author)
        assertEquals(2, pr.toDomain().number)
    }

    @Test
    fun issueDetailMergesCommentsTimeline() {
        val dto = IssueDetailDto(number = 7, title = "t", body = "body", user = OwnerDto(login = "u"))
        val comments = listOf(IssueCommentDto(user = OwnerDto(login = "c"), body = "hi"))
        val detail = dto.toDomain(comments.map { it.toDomain() })
        assertEquals(7, detail.number)
        assertEquals("body", detail.body)
        assertEquals(1, detail.comments.size)
        assertEquals("c", detail.comments.first().author)
    }

    @Test
    fun issueListApiHitsIssuesEndpoint() =
        runTest {
            val json =
                """
                [{"number":1,"title":"bug","state":"open","user":{"login":"alice"},"comments":2}]
                """.trimIndent()
            val engine =
                MockEngine {
                    respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val result = buildApi(engine).listIssues("o", "a")
            assertTrue(result is NetworkResult.Success)
            assertEquals(1, (result as NetworkResult.Success).data.size)
            assertTrue(
                engine.requestHistory
                    .first()
                    .url.encodedPath
                    .endsWith("/issues"),
            )
        }

    @Test
    fun contentEntryMapsDirectoryFields() {
        val entry =
            ContentEntryDto(name = "lib", path = "app/lib", type = "dir", size = 0, sha = "abc", downloadUrl = null)
        val domain = entry.toFileEntry()
        assertEquals("lib", domain.name)
        assertEquals("app/lib", domain.path)
        assertTrue(domain.isDirectory)
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    @Test
    fun fileContentDecodesBase64Text() {
        val encoded =
            kotlin.io.encoding.Base64.Default
                .encode("hello 文件\n第二行".toByteArray(Charsets.UTF_8))
        val dto =
            ContentEntryDto(name = "a.txt", path = "a.txt", type = "file", size = 10, encoding = "base64", content = encoded)
        val domain = dto.toFileContent()
        assertEquals("hello 文件\n第二行", domain.content)
        assertTrue(!domain.isTextUnavailable)
    }

    @Test
    fun fileContentWithoutBase64IsUnavailable() {
        val dto = ContentEntryDto(name = "big.bin", path = "big.bin", type = "file", size = 5_000_000, content = null)
        val domain = dto.toFileContent()
        assertTrue(domain.isTextUnavailable)
        assertEquals("", domain.content)
    }

    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    @Test
    fun binaryContentIsMarkedUnavailable() {
        val bytes = byteArrayOf(0x48, 0x69, 0x00, 0x01, 0x02) // "Hi" + NUL
        val dto =
            ContentEntryDto(
                name = "x.bin",
                path = "x.bin",
                type = "file",
                encoding = "base64",
                content =
                    kotlin.io.encoding.Base64.Default
                        .encode(bytes),
            )
        assertTrue(dto.toFileContent().isTextUnavailable)
    }

    @Test
    fun apiParsesContentsDirectoryWithPathEncodingAndOrdering() =
        runTest {
            val json =
                """
                [{"name":"z.txt","path":"dir/z.txt","type":"file","size":3},
                 {"name":"alpha","path":"dir/alpha","type":"dir","size":0},
                 {"name":"a.txt","path":"dir/a.txt","type":"file","size":5}]
                """.trimIndent()
            var requestedPath = ""
            val engine =
                MockEngine { request ->
                    requestedPath = request.url.encodedPathAndQuery
                    respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val result = buildApi(engine).listContents("o", "r", path = "dir with space/sub", ref = "main")

            assertTrue(result is NetworkResult.Success)
            val names = (result as NetworkResult.Success).data.map { it.name }
            assertEquals(listOf("alpha", "a.txt", "z.txt"), names) // 目录在前，文件按名字典序
            assertTrue(requestedPath.contains("/contents/dir%20with%20space/sub"))
            assertTrue(requestedPath.contains("ref=main"))
        }

    @Test
    fun apiParsesSingleFileContent() =
        runTest {
            @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
            val encoded =
                kotlin.io.encoding.Base64.Default
                    .encode("OK".toByteArray(Charsets.UTF_8))
            val json =
                """
                {"name":"a.txt","path":"a.txt","type":"file","size":2,"encoding":"base64","content":"$encoded"}
                """.trimIndent()
            val engine =
                MockEngine {
                    respond(json, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
                }
            val result = buildApi(engine).getFileContent("o", "r", "a.txt", "main")
            assertTrue(result is NetworkResult.Success)
            assertEquals("OK", (result as NetworkResult.Success).data.content)
        }

    @Test
    fun setStarredUsesMutationChannel() =
        runTest {
            var method = io.ktor.http.HttpMethod.Get
            var path = ""
            val engine =
                MockEngine { request ->
                    method = request.method
                    path = request.url.encodedPath
                    respond("", HttpStatusCode.NoContent)
                }
            val result = buildApi(engine).setStarred("o", "r", starred = true)

            assertTrue(result is MutationResult.Success)
            assertEquals(io.ktor.http.HttpMethod.Put, method)
            assertEquals("/user/starred/o/r", path)
        }

    @Test
    fun watchStatusUsesSubscriptionEndpoint() =
        runTest {
            var path = ""
            val engine =
                MockEngine { request ->
                    path = request.url.encodedPath
                    respond("", HttpStatusCode.NoContent)
                }
            val result = buildApi(engine).watchStatus("o", "r")

            assertTrue(result is NetworkResult.Success)
            assertEquals(true, (result as NetworkResult.Success).data)
            assertEquals("/repos/o/r/subscription", path)
        }

    @Test
    fun setWatchedSendsSubscribedBodyThenDelete() =
        runTest {
            val bodies = mutableListOf<String>()
            val methods = mutableListOf<io.ktor.http.HttpMethod>()
            val engine =
                MockEngine { request ->
                    methods += request.method
                    val content = request.body as? io.ktor.http.content.TextContent
                    bodies += content?.text.orEmpty()
                    respond("", HttpStatusCode.NoContent)
                }
            val api = buildApi(engine)

            assertTrue(api.setWatched("o", "r", watched = true) is MutationResult.Success)
            assertTrue(api.setWatched("o", "r", watched = false) is MutationResult.Success)

            assertEquals(listOf(io.ktor.http.HttpMethod.Put, io.ktor.http.HttpMethod.Delete), methods)
            assertEquals("""{"subscribed":true}""", bodies.first())
            assertEquals("", bodies.last())
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
