/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.data.remote

import com.gy.githubclient.core.data.network.GitHubJson
import com.gy.githubclient.feature.auth.domain.model.RateLimitInfo
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.serialization.Serializable

/** 探测端点结果：仅关心可达性与响应头，不解析业务体。 */
sealed interface ProbeResult {
    /** 200：已授予。data 为 /user 成功时解析的 login（其他端点为 null）。 */
    data class Granted(
        val login: String? = null,
    ) : ProbeResult

    /** 401/403：凭证有效但未授予该权限（401 也按未授予处理，避免误导为网络故障）。 */
    data object Denied : ProbeResult

    /** 429/5xx：限流或服务端错误，与「未授予」区分。 */
    data object RateLimited : ProbeResult

    /** 网络异常。 */
    data object NetworkError : ProbeResult
}

/** GET /user 最小响应（解析 login 用于体检报告展示）。 */
@Serializable
private data class ProbeUserDto(
    val login: String = "",
)

/** GET /rate_limit 响应的最小解析（两层包裹：resources.core）。 */
@Serializable
private data class RateLimitResponseDto(
    val resources: RateLimitResourcesDto = RateLimitResourcesDto(),
)

@Serializable
private data class RateLimitResourcesDto(
    val core: RateLimitCoreDto = RateLimitCoreDto(),
)

@Serializable
private data class RateLimitCoreDto(
    val limit: Int = 0,
    val remaining: Int = 0,
    val reset: Long = 0,
)

/**
 * 权限探测 API：对代表性端点发 GET，以状态码 + 响应头判定能力。
 * - GET /user：身份 + classic PAT 的 x-oauth-scopes 头。
 * - GET /notifications：通知权限。
 * - GET /user/starred?per_page=1：星标读写。
 * - GET /rate_limit：核心 API 限流余量（该端点本身不消耗配额）。
 */
class GitHubPermissionApi(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.github.com",
) {
    @Suppress("TooGenericExceptionCaught")
    suspend fun probeUser(token: String): ProbeResult =
        try {
            val response: HttpResponse = client.get("$baseUrl/user") { authHeaders(token) }
            when {
                response.status.value == 200 -> {
                    val login =
                        runCatching {
                            GitHubJson.decode(ProbeUserDto.serializer(), response.bodyAsText()).login
                        }.getOrNull()
                    ProbeResult.Granted(login)
                }

                response.status.value == 429 || response.status.value >= 500 -> ProbeResult.RateLimited
                else -> ProbeResult.Denied
            }
        } catch (t: Exception) {
            ProbeResult.NetworkError
        }

    /** classic PAT 的 scope 列表在 /user 响应头中返回；fine-grained PAT 无此头（返回空列表）。 */
    suspend fun oauthScopesFromHeader(token: String): List<String> = headerScopes(token)

    @Suppress("TooGenericExceptionCaught")
    suspend fun probeNotifications(token: String): ProbeResult =
        probe(token, "/notifications")

    @Suppress("TooGenericExceptionCaught")
    suspend fun probeStarred(token: String): ProbeResult = probe(token, "/user/starred?per_page=1")

    /**
     * 核心限流余量（GET /rate_limit，端点自身免配额）。
     * 任何失败均返回 null——限流信息是辅助展示，不得影响体检主报告。
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun rateLimit(token: String): RateLimitInfo? =
        try {
            val response: HttpResponse = client.get("$baseUrl/rate_limit") { authHeaders(token) }
            if (response.status.value == 200) {
                runCatching {
                    val dto = GitHubJson.decode(RateLimitResponseDto.serializer(), response.bodyAsText())
                    RateLimitInfo(
                        limit = dto.resources.core.limit,
                        remaining = dto.resources.core.remaining,
                        resetEpochSeconds = dto.resources.core.reset,
                    )
                }.getOrNull()
            } else {
                null
            }
        } catch (t: Exception) {
            null
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun probe(
        token: String,
        path: String,
    ): ProbeResult =
        try {
            val response: HttpResponse = client.get("$baseUrl$path") { authHeaders(token) }
            when {
                response.status.value == 200 -> ProbeResult.Granted()
                response.status.value == 429 || response.status.value >= 500 -> ProbeResult.RateLimited
                else -> ProbeResult.Denied
            }
        } catch (t: Exception) {
            ProbeResult.NetworkError
        }

    @Suppress("TooGenericExceptionCaught")
    private suspend fun headerScopes(token: String): List<String> =
        try {
            val response: HttpResponse = client.get("$baseUrl/user") { authHeaders(token) }
            response.headers[HEADER_X_OAUTH_SCOPES]
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        } catch (t: Exception) {
            emptyList()
        }

    private fun io.ktor.client.request.HttpRequestBuilder.authHeaders(token: String) {
        headers {
            append(HttpHeaders.Authorization, "Bearer $token")
            append(HttpHeaders.Accept, "application/vnd.github+json")
            append("X-GitHub-Api-Version", "2026-03-10")
        }
    }

    private companion object {
        const val HEADER_X_OAUTH_SCOPES = "X-OAuth-Scopes"
    }
}
