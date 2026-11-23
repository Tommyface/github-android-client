/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.domain.model

/**
 * GitHub OAuth Device Flow（RFC 8628）第一步得到的授权挑战。
 *
 * 用户需在浏览器打开 [verificationUri] 并输入 [userCode]，
 * 客户端在 [intervalSeconds] 间隔上轮询令牌端点，直到 [expiresInMillis] 到期。
 */
data class DeviceCodeChallenge(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInMillis: Long,
    val intervalSeconds: Int,
)

/** 发起 Device Flow 的结果。 */
sealed interface DeviceFlowStartResult {
    data class Success(
        val challenge: DeviceCodeChallenge,
    ) : DeviceFlowStartResult

    data class Error(
        val message: String,
    ) : DeviceFlowStartResult
}

/**
 * 单次令牌轮询结果。
 *
 * - [Pending]：用户尚未在浏览器完成授权，继续按间隔轮询。
 * - [SlowDown]：GitHub 要求放慢轮询（间隔 +5 秒）。
 * - [Expired]：device_code 已过期，须重新发起。
 * - [Denied]：用户拒绝授权。
 * - [Authorized]：拿到 access_token（明文仅途经内存，随后交安全存储）。
 * - [NetworkError]：网络/协议错误，可重试。
 */
sealed interface DeviceFlowPollResult {
    data object Pending : DeviceFlowPollResult

    data object SlowDown : DeviceFlowPollResult

    data object Expired : DeviceFlowPollResult

    data object Denied : DeviceFlowPollResult

    data class Authorized(
        val accessToken: String,
    ) : DeviceFlowPollResult

    data class NetworkError(
        val message: String,
    ) : DeviceFlowPollResult
}
