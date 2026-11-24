/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.data

import com.gy.githubclient.feature.auth.data.remote.GitHubPermissionApi
import com.gy.githubclient.feature.auth.data.remote.ProbeResult
import com.gy.githubclient.feature.auth.domain.model.AuthMethod
import com.gy.githubclient.feature.auth.domain.model.AuthSession
import com.gy.githubclient.feature.auth.domain.model.AuthUser
import com.gy.githubclient.feature.auth.domain.model.TokenChannel
import com.gy.githubclient.feature.auth.domain.repository.AuthRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 权限体检：三种凭证（OAuth / classic PAT / fine-grained PAT）判定路径。 */
class PermissionAuditTest {
    private val userJson = """{"login":"gy","name":"Gy","avatar_url":null,"html_url":"https://github.com/gy"}"""

    private class RecordingAuthRepository(
        private val method: AuthMethod?,
        private val scopes: List<String>,
        private val primaryToken: String? = "token-under-test",
    ) : AuthRepository {
        override fun observeAuthState() = error("not used")

        override suspend fun restoreSession() = error("not used")

        override suspend fun signInWithPat(token: String) = error("not used")

        override suspend fun signInWithOAuth(
            accessToken: String,
            scopes: List<String>,
        ) = error("not used")

        override suspend fun bindNotificationToken(token: String) = error("not used")

        override suspend fun getPrimaryToken(): String? = primaryToken

        override suspend fun getNotificationToken(): String? = null

        override suspend fun signOut(channel: TokenChannel?) = error("not used")

        override suspend fun setAuthMethod(method: AuthMethod) = Unit

        override suspend fun currentSession(): AuthSession =
            AuthSession(
                user = AuthUser("gy", "Gy", null, "https://github.com/gy"),
                hasPrimaryToken = true,
                hasNotificationToken = false,
                method = method,
                scopes = scopes,
            )
    }

    /** 按请求路径路由响应的 MockEngine。 */
    private fun routingEngine(
        routes: Map<String, Pair<Int, List<Pair<String, String>>>>,
    ): MockEngine =
        MockEngine { request ->
            val (status, headers) = routes[request.url.encodedPath] ?: (404 to emptyList())
            respond(
                content = if (status == 200) userJson else """{"message":"nope"}""",
                status = HttpStatusCode.fromValue(status),
                headers = headersOf(*headers.map { (k, v) -> k to listOf(v) }.toTypedArray()),
            )
        }

    @Test
    fun oauthUsesSavedScopesWithoutProbingEndpoints() =
        runTest {
            val engine = routingEngine(mapOf("/user" to (200 to emptyList())))
            val api = GitHubPermissionApi(HttpClient(engine))
            val report =
                PermissionAuditRepositoryImpl(
                    RecordingAuthRepository(AuthMethod.OAUTH, listOf("repo", "read:user", "notifications", "workflow")),
                    api,
                ).audit()

            assertEquals("gy", report.login)
            // 4 项授予（账号/仓库/星标/通知/工作流中除邮箱外共 5 项——邮箱缺 user:email）。
            assertEquals(5, report.grantedCount)
            val workflow = report.capabilities.first { it.name == "Actions 工作流" }
            assertTrue(workflow.granted)
            assertTrue(workflow.basis.contains("OAuth scope"))

            // 缺 user:email → 该项未授予。
            val email = report.capabilities.first { it.name == "邮箱读取" }
            assertFalse(email.granted)
        }

    @Test
    fun classicPatReadsScopesFromResponseHeader() =
        runTest {
            val engine =
                routingEngine(
                    mapOf(
                        "/user" to (200 to listOf("X-OAuth-Scopes" to "repo, notifications")),
                    ),
                )
            val api = GitHubPermissionApi(HttpClient(engine))
            val report =
                PermissionAuditRepositoryImpl(
                    RecordingAuthRepository(AuthMethod.PAT, emptyList(), primaryToken = "ghp_classictoken"),
                    api,
                ).audit()

            assertEquals(listOf("repo", "notifications"), report.scopes)
            assertTrue(report.capabilities.first { it.name == "通知读取" }.granted)
            // classic 头不含 workflow → 工作流未授予。
            assertFalse(report.capabilities.first { it.name == "Actions 工作流" }.granted)
        }

    @Test
    fun fineGrainedPatFallsBackToEndpointProbing() =
        runTest {
            val engine =
                routingEngine(
                    mapOf(
                        "/user" to (200 to emptyList()),
                        "/user/starred" to (200 to emptyList()),
                        "/notifications" to (403 to emptyList()),
                    ),
                )
            val api = GitHubPermissionApi(HttpClient(engine))
            val report =
                PermissionAuditRepositoryImpl(
                    RecordingAuthRepository(AuthMethod.PAT, emptyList()),
                    api,
                ).audit()

            // fine-grained 无 scope 元数据 → scopes 为空，能力以探测为准。
            assertTrue(report.scopes.isEmpty())
            assertTrue(report.capabilities.first { it.name == "账号信息" }.granted)
            assertTrue(report.capabilities.first { it.name == "星标收藏" }.granted)
            assertFalse(report.capabilities.first { it.name == "通知读取" }.granted)
            assertTrue(
                report.capabilities
                    .first { it.name == "通知读取" }
                    .basis
                    .contains("401/403"),
            )
        }

    @Test
    fun probeMapsStatusCodesCorrectly() =
        runTest {
            val engine =
                routingEngine(
                    mapOf(
                        "/user" to (429 to emptyList()),
                        "/user/starred" to (500 to emptyList()),
                        "/notifications" to (200 to emptyList()),
                    ),
                )
            val api = GitHubPermissionApi(HttpClient(engine))

            assertTrue(api.probeUser("t") is ProbeResult.RateLimited)
            assertTrue(api.probeStarred("t") is ProbeResult.RateLimited)
            assertTrue(api.probeNotifications("t") is ProbeResult.Granted)
        }

    @Test
    fun signedOutProducesEmptyReport() =
        runTest {
            val engine = routingEngine(emptyMap())
            val api = GitHubPermissionApi(HttpClient(engine))
            val repo = RecordingAuthRepository(null, emptyList())
            // 未登录：getPrimaryToken 为 null。
            val anonymous =
                object : AuthRepository by repo {
                    override suspend fun getPrimaryToken(): String? = null
                }
            val report = PermissionAuditRepositoryImpl(anonymous, api).audit()

            assertNull(report.login)
            assertTrue(report.capabilities.isEmpty())
        }
}
