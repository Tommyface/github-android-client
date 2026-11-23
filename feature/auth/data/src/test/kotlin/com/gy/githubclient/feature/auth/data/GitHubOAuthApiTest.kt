/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.data

import com.gy.githubclient.feature.auth.data.remote.DeviceCodeResult
import com.gy.githubclient.feature.auth.data.remote.GitHubOAuthApi
import com.gy.githubclient.feature.auth.data.remote.OAuthTokenResult
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** 回归：Device Flow 两端点的请求构造与响应解析（JSON / form-encoded 双格式）。 */
class GitHubOAuthApiTest {
    private lateinit var server: MockWebServer
    private lateinit var client: HttpClient
    private lateinit var api: GitHubOAuthApi

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client =
            HttpClient(OkHttp) {
                install(HttpTimeout) {
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 30_000
                    requestTimeoutMillis = 30_000
                }
            }
        api = GitHubOAuthApi(client, baseUrl = server.url("/").toString().removeSuffix("/"))
    }

    @After
    fun tearDown() {
        client.close()
        server.shutdown()
    }

    @Test
    fun deviceCodePostsFormAndParsesJson() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setBody(
                        """{"device_code":"dev-123","user_code":"ABCD-1234",""" +
                            """"verification_uri":"https://github.com/login/device",""" +
                            """"expires_in":898,"interval":5}""",
                    ).setHeader(HttpHeaders.ContentType, "application/json;charset=utf-8"),
            )

            val result = api.requestDeviceCode("test-client", "repo read:user")

            val recorded = server.takeRequest()
            assertEquals("/login/device/code", recorded.path)
            assertEquals("application/json", recorded.getHeader(HttpHeaders.Accept))
            val body = recorded.body.readUtf8()
            assertTrue(body.contains("client_id=test-client"))
            assertTrue(body.contains("scope=repo+read%3Auser"))

            assertTrue(result is DeviceCodeResult.Success)
            val dto = (result as DeviceCodeResult.Success).dto
            assertEquals("dev-123", dto.deviceCode)
            assertEquals("ABCD-1234", dto.userCode)
            assertEquals("https://github.com/login/device", dto.verificationUri)
            assertEquals(898, dto.expiresIn)
            assertEquals(5, dto.interval)
        }

    @Test
    fun deviceCodeHttpErrorMapsToError() =
        runBlocking {
            server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
            val result = api.requestDeviceCode("test-client", "repo")
            assertTrue(result is DeviceCodeResult.Error)
            assertEquals(503, (result as DeviceCodeResult.Error).code)
        }

    @Test
    fun tokenRequestPostsGrantTypeAndParsesJson() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setBody("""{"access_token":"gho_json","token_type":"bearer","scope":"repo"}""")
                    .setHeader(HttpHeaders.ContentType, "application/json;charset=utf-8"),
            )

            val result = api.requestAccessToken("test-client", "dev-123")

            val recorded = server.takeRequest()
            assertEquals("/login/oauth/access_token", recorded.path)
            val body = recorded.body.readUtf8()
            assertTrue(body.contains("client_id=test-client"))
            assertTrue(body.contains("device_code=dev-123"))
            assertTrue(
                body.contains(
                    "grant_type=urn%3Aietf%3Aparams%3Aoauth%3Agrant-type%3Adevice_code",
                ),
            )

            assertTrue(result is OAuthTokenResult.Success)
            assertEquals("gho_json", (result as OAuthTokenResult.Success).dto.accessToken)
        }

    @Test
    fun tokenFormEncodedResponseStillParses() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setBody("access_token=gho_form&token_type=bearer&scope=repo")
                    .setHeader(HttpHeaders.ContentType, "application/x-www-form-urlencoded;charset=utf-8"),
            )

            val result = api.requestAccessToken("test-client", "dev-123")

            assertTrue(result is OAuthTokenResult.Success)
            val dto = (result as OAuthTokenResult.Success).dto
            assertEquals("gho_form", dto.accessToken)
            assertEquals("bearer", dto.tokenType)
        }

    @Test
    fun tokenErrorBodyParsesErrorField() =
        runBlocking {
            server.enqueue(
                MockResponse()
                    .setBody("""{"error":"authorization_pending","error_description":"wait"}""")
                    .setHeader(HttpHeaders.ContentType, "application/json;charset=utf-8"),
            )
            val result = api.requestAccessToken("test-client", "dev-123")
            assertTrue(result is OAuthTokenResult.Success)
            assertEquals("authorization_pending", (result as OAuthTokenResult.Success).dto.error)
        }
}
