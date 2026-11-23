/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.data

import com.gy.githubclient.feature.auth.data.remote.GitHubAuthApi
import com.gy.githubclient.feature.auth.data.remote.GitHubUserDto
import com.gy.githubclient.feature.auth.data.remote.RemoteUserResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 回归：OkHttp 引擎（真机路径）下 200 响应经 GitHubAuthApi 完整解析。 */
class AuthOkHttpSerializationTest {
    @Test
    fun directJsonParse() {
        val dto = Json.decodeFromString<GitHubUserDto>("""{"login":"gy"}""")
        assertEquals("gy", dto.login)
    }

    @Test
    fun okhttpEngine200MapsToSuccessViaAuthApi() {
        val server = MockWebServer()
        server.enqueue(
            MockResponse()
                .setBody("""{"login":"gy","name":"Gy","avatar_url":"http://a.png","html_url":"https://github.com/gy"}""")
                .setHeader(HttpHeaders.ContentType, "application/json;charset=utf-8"),
        )
        server.start()
        val client =
            HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }
                install(HttpTimeout) {
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 30_000
                    requestTimeoutMillis = 30_000
                }
            }
        try {
            runBlocking {
                val api = GitHubAuthApi(client, baseUrl = server.url("/").toString().removeSuffix("/"))
                val result = api.getCurrentUser("ghp_any")

                assertTrue(result is RemoteUserResult.Success)
                assertEquals("gy", (result as RemoteUserResult.Success).user.login)
            }
        } finally {
            client.close()
            server.shutdown()
        }
    }
}
