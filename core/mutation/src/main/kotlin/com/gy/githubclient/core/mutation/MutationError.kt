/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.mutation

/** 403 的细分原因，便于 UI 给出可行动提示。 */
enum class ForbiddenReason {
    MISSING_SCOPE,
    PROTECTED_BRANCH,
    INSUFFICIENT_ROLE,
    OTHER,
}

/** GitHub 422 响应里的字段级错误。 */
data class FieldError(
    val field: String,
    val code: String,
    val message: String? = null,
)

/** GitHub 错误响应体的最小结构，由数据层解析后传入。 */
data class GitHubErrorPayload(
    val message: String? = null,
    val errors: List<FieldError> = emptyList(),
)

/** 写操作的统一错误模型，不依赖 Ktor/Android。 */
sealed interface MutationError {
    data object Unauthorized : MutationError

    data class Forbidden(
        val reason: ForbiddenReason,
        val message: String? = null,
        /** 服务端声明该端点需要、但当前凭证未授予的 scope。 */
        val missingScopes: List<String> = emptyList(),
    ) : MutationError

    data object NotFound : MutationError

    data class Conflict(
        val message: String? = null,
    ) : MutationError

    data class Validation(
        val message: String? = null,
        val fieldErrors: List<FieldError> = emptyList(),
    ) : MutationError

    data class RateLimited(
        val retryAfterSeconds: Long? = null,
        val remaining: Int? = null,
        val resetEpochSeconds: Long? = null,
    ) : MutationError

    data class Server(
        val status: Int,
        val message: String? = null,
    ) : MutationError

    data class Network(
        val cause: String? = null,
    ) : MutationError

    data class Unknown(
        val status: Int? = null,
        val message: String? = null,
    ) : MutationError
}
