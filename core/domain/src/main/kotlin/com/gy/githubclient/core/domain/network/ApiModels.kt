/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.network

/**
 * 统一错误模型。所有 Feature 的仓库层共用，UI 据此决定提示、重试或跳登录。
 *
 * 对应《技术架构设计》5.1：RateLimited / Unauthorized / NotFound / Network / Unknown。
 */
sealed interface ApiError {
    /** 401：token 失效或无权限，通常触发重新登录。 */
    data object Unauthorized : ApiError

    /** 403 + 限流头 或 429：API 额度耗尽。 [retryAfterSeconds] 来自 X-RateLimit-Reset。 */
    data class RateLimited(
        val retryAfterSeconds: Long?,
    ) : ApiError

    /** 404：资源不存在。 */
    data object NotFound : ApiError

    /** 网络不可达 / DNS / 超时。 */
    data class Network(
        val cause: String,
    ) : ApiError

    /** 其它 HTTP 错误（5xx、422 等）。 */
    data class Http(
        val code: Int,
        val message: String,
    ) : ApiError

    /** 解析或未知异常。 */
    data class Unknown(
        val message: String,
    ) : ApiError
}

/** 网络调用结果：成功携带数据，失败携带统一错误。 */
sealed interface NetworkResult<out T> {
    data class Success<T>(
        val data: T,
    ) : NetworkResult<T>

    data class Failure(
        val error: ApiError,
    ) : NetworkResult<Nothing>
}

/** 条件请求结果：拿到新数据，或 304 未修改（可复用本地缓存，不消耗 API 额度）。 */
sealed interface Conditional<out T> {
    data class Data<T>(
        val value: T,
    ) : Conditional<T>

    data object NotModified : Conditional<Nothing>
}

/** token 使用通道：主通道走 fine-grained，通知通道走 classic PAT。 */
enum class ApiChannel {
    PRIMARY,
    NOTIFICATION,
}

/**
 * token 供应接口（依赖倒置）。
 *
 * core 网络层不依赖 auth feature，只依赖此接口；由 auth 模块实现并注入，
 * 从而避免 core → feature 的反向依赖。
 */
interface AuthTokenProvider {
    /** 返回指定通道的 token；未就绪返回 null。 */
    suspend fun tokenFor(channel: ApiChannel): String?

    /** token 失效回调（401 时网络层调用），由 auth 层清理会话。 */
    suspend fun onUnauthorized(channel: ApiChannel)
}
