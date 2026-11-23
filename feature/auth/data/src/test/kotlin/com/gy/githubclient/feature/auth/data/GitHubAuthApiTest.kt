/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.data

import com.gy.githubclient.feature.auth.data.remote.GitHubAuthApi
import com.gy.githubclient.feature.auth.data.remote.RemoteUserResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 远程 token 校验映射测试：HTTP 状态码 → 领域结果。 */
class GitHubAuthApiTest {
    private fun apiReturning(
        status: HttpStatusCode,
        body: String,
    ): GitHubAuthApi {
        val client =
            HttpClient(MockEngine) {
                install(ContentNegotiation) {
                    json(Json { ignoreUnknownKeys = true })
                }
                engine {
                    addHandler {
                        respond(
                            content = body,
                            status = status,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
            }
        return GitHubAuthApi(client)
    }

    @Test
    fun okResponseMapsToSuccess() =
        runTest {
            val api =
                apiReturning(
                    HttpStatusCode.OK,
                    """{"login":"gy","name":"Gy","avatar_url":null,"html_url":"https://github.com/gy"}""",
                )
            val result = api.getCurrentUser("ghp_any")

            assertTrue(result is RemoteUserResult.Success)
            assertEquals("gy", (result as RemoteUserResult.Success).user.login)
        }

    @Test
    fun unauthorizedMapsToInvalidToken() =
        runTest {
            val api = apiReturning(HttpStatusCode.Unauthorized, """{"message":"Bad credentials"}""")
            val result = api.getCurrentUser("bad")

            assertTrue(result is RemoteUserResult.Unauthorized)
        }

    @Test
    fun forbiddenMapsToRateLimited() =
        runTest {
            val api = apiReturning(HttpStatusCode.Forbidden, """{"message":"rate limited"}""")
            val result = api.getCurrentUser("ghp_any")

            assertTrue(result is RemoteUserResult.RateLimited)
        }
}
