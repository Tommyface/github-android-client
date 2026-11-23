/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.data.remote

import com.gy.githubclient.core.data.network.GitHubJson
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.URLDecoder

/** POST /login/device/code 成功响应（Accept: application/json 时为 JSON）。 */
@Serializable
data class DeviceCodeResponseDto(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("verification_uri") val verificationUri: String,
    @SerialName("verification_uri_complete") val verificationUriComplete: String? = null,
    @SerialName("expires_in") val expiresIn: Int = 900,
    @SerialName("interval") val interval: Int = 5,
)

/** POST /login/oauth/access_token 响应：成功带 access_token；失败带 error。 */
@Serializable
data class OAuthTokenResponseDto(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("token_type") val tokenType: String? = null,
    @SerialName("scope") val scope: String? = null,
    @SerialName("error") val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    @SerialName("interval") val interval: Int? = null,
)

/** /login/device/code 调用结果。 */
sealed interface DeviceCodeResult {
    data class Success(
        val dto: DeviceCodeResponseDto,
    ) : DeviceCodeResult

    data class Error(
        val code: Int,
        val message: String,
    ) : DeviceCodeResult
}

/** /login/oauth/access_token 调用结果。 */
sealed interface OAuthTokenResult {
    data class Success(
        val dto: OAuthTokenResponseDto,
    ) : OAuthTokenResult

    data class Error(
        val code: Int,
        val message: String,
    ) : OAuthTokenResult
}

/**
 * GitHub OAuth Device Flow API（端点在 github.com，而非 api.github.com）。
 *
 * 请求为表单编码 POST；响应统一 bodyAsText() + 显式 serializer 解析，
 * 绕开 Ktor 3.0.3 真机 ContentNegotiation 缺陷（与 GitHubAuthApi 同策略），
 * 并兼容 GitHub 偶发的 form-encoded 响应。
 */
class GitHubOAuthApi(
    private val client: HttpClient,
    private val baseUrl: String = "https://github.com",
) {
    @Suppress("TooGenericExceptionCaught")
    suspend fun requestDeviceCode(
        clientId: String,
        scope: String,
    ): DeviceCodeResult =
        try {
            val response: HttpResponse =
                client.post("$baseUrl/login/device/code") {
                    headers { append(HttpHeaders.Accept, "application/json") }
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("client_id", clientId)
                                append("scope", scope)
                            },
                        ),
                    )
                }
            val text = response.bodyAsText()
            if (response.status != HttpStatusCode.OK) {
                DeviceCodeResult.Error(response.status.value, text.take(MAX_ERROR_BODY))
            } else {
                runCatching { GitHubJson.decode(DeviceCodeResponseDto.serializer(), text) }
                    .fold(
                        onSuccess = { DeviceCodeResult.Success(it) },
                        onFailure = { DeviceCodeResult.Error(-1, it.message ?: "解析失败") },
                    )
            }
        } catch (t: Exception) {
            DeviceCodeResult.Error(-1, t.message ?: "network error")
        }

    @Suppress("TooGenericExceptionCaught")
    suspend fun requestAccessToken(
        clientId: String,
        deviceCode: String,
    ): OAuthTokenResult =
        try {
            val response: HttpResponse =
                client.post("$baseUrl/login/oauth/access_token") {
                    headers { append(HttpHeaders.Accept, "application/json") }
                    setBody(
                        FormDataContent(
                            Parameters.build {
                                append("client_id", clientId)
                                append("device_code", deviceCode)
                                append("grant_type", DEVICE_GRANT_TYPE)
                            },
                        ),
                    )
                }
            val text = response.bodyAsText()
            if (response.status != HttpStatusCode.OK) {
                OAuthTokenResult.Error(response.status.value, text.take(MAX_ERROR_BODY))
            } else {
                runCatching { parseTokenBody(text) }
                    .fold(
                        onSuccess = { OAuthTokenResult.Success(it) },
                        onFailure = { OAuthTokenResult.Error(-1, it.message ?: "解析失败") },
                    )
            }
        } catch (t: Exception) {
            OAuthTokenResult.Error(-1, t.message ?: "network error")
        }

    /** 优先 JSON 解析；非 JSON（GitHub 少数路径回 form-encoded）时按 URLSearchParams 解析。 */
    private fun parseTokenBody(text: String): OAuthTokenResponseDto {
        val trimmed = text.trim()
        if (trimmed.startsWith("{")) {
            return GitHubJson.decode(OAuthTokenResponseDto.serializer(), trimmed)
        }
        val form = parseForm(trimmed)
        return OAuthTokenResponseDto(
            accessToken = form["access_token"],
            tokenType = form["token_type"],
            scope = form["scope"],
            error = form["error"],
            errorDescription = form["error_description"],
        )
    }

    private fun parseForm(text: String): Map<String, String> =
        text
            .split("&")
            .mapNotNull { pair ->
                val idx = pair.indexOf('=')
                if (idx <= 0) {
                    null
                } else {
                    val key = URLDecoder.decode(pair.substring(0, idx), "UTF-8")
                    val value = URLDecoder.decode(pair.substring(idx + 1), "UTF-8")
                    key to value
                }
            }.toMap()

    companion object {
        /** RFC 8628 Device Flow 固定 grant_type。 */
        const val DEVICE_GRANT_TYPE = "urn:ietf:params:oauth:grant-type:device_code"

        /** OAuth App client_id 属公开信息（Device Flow 无需 client_secret），由 app 层 BuildConfig 注入，此值仅作兜底。 */
        const val DEFAULT_CLIENT_ID = "Ov23li50RuIKE1lG7qe4"

        private const val MAX_ERROR_BODY = 200
    }
}
