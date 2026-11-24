/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.data

import com.gy.githubclient.feature.auth.data.remote.DeviceCodeResult
import com.gy.githubclient.feature.auth.data.remote.GitHubOAuthApi
import com.gy.githubclient.feature.auth.data.remote.OAuthTokenResponseDto
import com.gy.githubclient.feature.auth.data.remote.OAuthTokenResult
import com.gy.githubclient.feature.auth.domain.model.DeviceCodeChallenge
import com.gy.githubclient.feature.auth.domain.model.DeviceFlowPollResult
import com.gy.githubclient.feature.auth.domain.model.DeviceFlowStartResult
import com.gy.githubclient.feature.auth.domain.repository.OAuthDeviceFlowRepository
import kotlinx.coroutines.delay

/**
 * Device Flow 仓库实现：把 API 层结果翻译成领域模型。
 *
 * 轮询得到的 access_token 只途经内存，登录管线（校验 + EncryptedSharedPreferences 存储）
 * 由 AuthRepositoryImpl.signInWithOAuth 承担。
 *
 * start 阶段对传输层异常自动重试：直连 github.com 在国内常超时/被重置，
 * 单发请求一次失败即中止体验太差，故退避重试至多 3 次（2s、4s）。
 */
class OAuthDeviceFlowRepositoryImpl(
    private val api: GitHubOAuthApi,
    private val clientId: String,
    private val scope: String = DEFAULT_SCOPE,
    /** 重试退避基数，测试可注入 0 加速用例。 */
    private val retryDelayMillis: Long = RETRY_BACKOFF_MILLIS,
) : OAuthDeviceFlowRepository {
    override suspend fun startDeviceFlow(): DeviceFlowStartResult {
        var lastError: DeviceCodeResult.Error? = null
        for (attempt in 1..MAX_START_ATTEMPTS) {
            if (attempt > 1) {
                delay(retryDelayMillis * (attempt - 1))
            }
            when (val result = api.requestDeviceCode(clientId, scope)) {
                is DeviceCodeResult.Success ->
                    return DeviceFlowStartResult.Success(
                        DeviceCodeChallenge(
                            deviceCode = result.dto.deviceCode,
                            userCode = result.dto.userCode,
                            verificationUri = result.dto.verificationUri,
                            expiresInMillis = result.dto.expiresIn * MILLIS_PER_SECOND,
                            intervalSeconds = result.dto.interval,
                        ),
                    )

                is DeviceCodeResult.Error -> {
                    lastError = result
                    // 仅传输层异常（TRANSPORT_ERROR_CODE）重试；
                    // HTTP 状态码错误与解析失败重试无意义，立即中止。
                    if (result.code != TRANSPORT_ERROR_CODE) break
                }
            }
        }
        val error = checkNotNull(lastError) { "重试循环结束时必须已记录错误" }
        return DeviceFlowStartResult.Error("[${error.code}] ${error.message}")
    }

    override suspend fun pollForToken(deviceCode: String): DeviceFlowPollResult =
        when (val result = api.requestAccessToken(clientId, deviceCode)) {
            is OAuthTokenResult.Success -> mapTokenBody(result.dto)

            is OAuthTokenResult.Error ->
                DeviceFlowPollResult.NetworkError("[${result.code}] ${result.message}")
        }

    private fun mapTokenBody(dto: OAuthTokenResponseDto): DeviceFlowPollResult =
        when {
            dto.accessToken != null ->
                DeviceFlowPollResult.Authorized(
                    accessToken = dto.accessToken,
                    // GitHub 以空格分隔返回实际授予的 scope，可能小于请求集合。
                    scopes =
                        dto.scope
                            .orEmpty()
                            .split(' ')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() },
                )
            dto.error == null ->
                DeviceFlowPollResult.NetworkError("响应缺少 access_token 与 error 字段")
            else ->
                when (dto.error) {
                    ERROR_PENDING -> DeviceFlowPollResult.Pending
                    ERROR_SLOW_DOWN -> DeviceFlowPollResult.SlowDown
                    ERROR_EXPIRED -> DeviceFlowPollResult.Expired
                    ERROR_DENIED -> DeviceFlowPollResult.Denied
                    else ->
                        DeviceFlowPollResult.NetworkError(
                            "${dto.error}: ${dto.errorDescription.orEmpty()}",
                        )
                }
        }

    private companion object {
        const val MILLIS_PER_SECOND = 1000L

        /** start 阶段最大尝试次数（首用 + 2 次重试）。 */
        const val MAX_START_ATTEMPTS = 3

        /** 重试退避基数：第 2 次尝试前等 2s，第 3 次前等 4s。 */
        const val RETRY_BACKOFF_MILLIS = 2_000L

        /** API 层约定：-1 为网络传输异常（可重试），-2 为响应解析失败。 */
        const val TRANSPORT_ERROR_CODE = -1

        /** 覆盖客户端全部功能面：仓库读写、用户信息、邮箱、Actions 工作流、通知。 */
        const val DEFAULT_SCOPE = "repo read:user user:email workflow notifications"

        const val ERROR_PENDING = "authorization_pending"
        const val ERROR_SLOW_DOWN = "slow_down"
        const val ERROR_EXPIRED = "expired_token"
        const val ERROR_DENIED = "access_denied"
    }
}
