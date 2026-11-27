/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.domain.network.ApiChannel
import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.core.mutation.MutationResult
import com.gy.githubclient.feature.repo.data.remote.GitHubWriteApi
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M2-05/07/08：仓库写操作（请求形状 + 错误映射）。 */
class GitHubWriteApiTest {
    @Test
    fun updateRepositorySendsPatchWithOnlyChangedFields() =
        runTest {
            var capturedMethod: String? = null
            var capturedUrl: String? = null
            var capturedBody: String? = null
            val engine =
                MockEngine { request ->
                    capturedMethod = request.method.value
                    capturedUrl = request.url.encodedPath
                    capturedBody = request.body.toString()
                    respond("{}", HttpStatusCode.OK, headersOf())
                }
            val api = GitHubWriteApi(buildClient(engine))

            val result = api.updateRepository("o", "r", description = "hello")

            assertTrue(result is MutationResult.Success)
            assertEquals("PATCH", capturedMethod)
            assertEquals("/repos/o/r", capturedUrl)
            assertTrue("body 应含 description：$capturedBody", capturedBody?.contains("\"description\":\"hello\"") == true)
            assertTrue("未变更字段不应出现：$capturedBody", capturedBody?.contains("private") != true)
        }

    @Test
    fun createRepositoryIsNotRetriedOnFailure() =
        runTest {
            var attempts = 0
            val engine =
                MockEngine {
                    attempts++
                    respond("{}", HttpStatusCode.UnprocessableEntity, headersOf())
                }
            val api = GitHubWriteApi(buildClient(engine))

            val result = api.createRepository("new-repo", description = null, private = true)

            assertTrue(result is MutationResult.Failure)
            assertEquals("建库不可幂等，失败不应重试", 1, attempts)
        }

    @Test
    fun uploadFileUsesContentsApi() =
        runTest {
            var capturedUrl: String? = null
            var capturedMethod: String? = null
            val engine =
                MockEngine { request ->
                    capturedUrl = request.url.encodedPath
                    capturedMethod = request.method.value
                    respond("{}", HttpStatusCode.Created, headersOf())
                }
            val api = GitHubWriteApi(buildClient(engine))

            val result = api.uploadFile("o", "r", "docs/a.md", "Y29kZQ==", "add a")

            assertTrue(result is MutationResult.Success)
            assertEquals("PUT", capturedMethod)
            assertEquals("/repos/o/r/contents/docs/a.md", capturedUrl)
        }

    @Test
    fun deleteRepositoryMapsForbidden() =
        runTest {
            val engine =
                MockEngine {
                    respond("""{"message":"Must have admin rights"}""", HttpStatusCode.Forbidden, headersOf())
                }
            val api = GitHubWriteApi(buildClient(engine))

            val result = api.deleteRepository("o", "r")

            assertTrue(result is MutationResult.Failure)
        }

    private fun buildClient(engine: MockEngine): GitHubApiClient =
        GitHubApiClient(
            httpClient = HttpClient(engine) { expectSuccess = false },
            tokenProvider = object : AuthTokenProvider {
                override suspend fun tokenFor(channel: ApiChannel): String = "ghp_test"

                override suspend fun onUnauthorized(channel: ApiChannel) = Unit
            },
        )
}
