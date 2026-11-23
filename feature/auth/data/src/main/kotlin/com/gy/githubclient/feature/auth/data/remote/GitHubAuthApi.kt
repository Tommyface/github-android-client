/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.data.remote

import com.gy.githubclient.core.data.network.GitHubJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /user 返回的最小字段。 */
@Serializable
data class GitHubUserDto(
    val login: String,
    @SerialName("name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
)

/** /user 调用结果：成功拿到用户，或携带可映射的错误信息。 */
sealed interface RemoteUserResult {
    data class Success(
        val user: GitHubUserDto,
    ) : RemoteUserResult

    data class Unauthorized(
        val retryAfter: Long?,
    ) : RemoteUserResult

    data class RateLimited(
        val retryAfter: Long?,
    ) : RemoteUserResult

    data class Error(
        val code: Int,
        val message: String,
    ) : RemoteUserResult
}

/**
 * 仅负责「用某 token 拉取当前用户」这一个动作——token 校验即登录。
 * 不持有 token、不做存储，便于单测。
 */
class GitHubAuthApi(
    private val client: HttpClient,
    private val baseUrl: String = "https://api.github.com",
) {
    @Suppress("TooGenericExceptionCaught")
    suspend fun getCurrentUser(token: String): RemoteUserResult =
        try {
            val response: HttpResponse =
                client.get("$baseUrl/user") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $token")
                        append(HttpHeaders.Accept, "application/vnd.github+json")
                        append("X-GitHub-Api-Version", "2026-03-10")
                    }
                }
            mapResponse(response)
        } catch (t: Exception) {
            // 网络层统一兜底：Ktor 可能抛 ClientRequestException / IOException 等多种类型。
            RemoteUserResult.Error(code = -1, message = t.message ?: "network error")
        }

    private suspend fun mapResponse(response: HttpResponse): RemoteUserResult {
        val retryAfter =
            response.headers["X-RateLimit-Reset"]?.toLongOrNull()?.let { resetEpochSeconds ->
                val nowSeconds = System.currentTimeMillis() / MILLIS_PER_SECOND
                (resetEpochSeconds - nowSeconds).coerceAtLeast(0L)
            }
        return when (response.status) {
            HttpStatusCode.OK ->
                RemoteUserResult.Success(
                    GitHubJson.decode(GitHubUserDto.serializer(), response.bodyAsText()),
                )
            HttpStatusCode.Unauthorized -> RemoteUserResult.Unauthorized(retryAfter)
            HttpStatusCode.Forbidden -> RemoteUserResult.RateLimited(retryAfter)
            else ->
                RemoteUserResult.Error(
                    code = response.status.value,
                    message = response.status.description,
                )
        }
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L
    }
}
