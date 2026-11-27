/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.mutation

import kotlinx.coroutines.delay

/** 写操作描述：审计与重试策略都以此为准。 */
data class MutationSpec(
    /** 动作标识，如 "star" / "watch" / "follow" / "notification.ignore"。 */
    val action: String,
    /** 目标标识，如 "owner/repo" 或 "@login"；不得包含 token。 */
    val target: String,
    /** 是否幂等：非幂等操作遇到网络错误/5xx 不自动重试。 */
    val idempotent: Boolean = true,
    /** 是否属于危险操作（UI 需二次确认）。 */
    val dangerous: Boolean = false,
)

/** 单次尝试结果。 */
sealed interface MutationAttempt<out T> {
    data class Success<T>(
        val value: T,
        val statusCode: Int? = null,
        val requestId: String? = null,
    ) : MutationAttempt<T>

    data class Failure(
        val error: MutationError,
        val statusCode: Int? = null,
        val requestId: String? = null,
    ) : MutationAttempt<Nothing>
}

/**
 * 写操作信封：统一重试、错误收敛与本地审计。
 *
 * 数据层只负责「发一次请求并归类结果」，重试判断与审计由本类集中处理，
 * 避免每个页面各写一套。非幂等操作的重试限制见 [MutationRetryPolicy]。
 */
class MutationRunner(
    private val retryPolicy: MutationRetryPolicy = MutationRetryPolicy(),
    private val auditSink: AuditSink = NoOpAuditSink,
    private val clock: () -> Long = { System.currentTimeMillis() },
    private val sleeper: suspend (Long) -> Unit = { delay(it) },
) {
    suspend fun <T> execute(
        spec: MutationSpec,
        block: suspend (attempt: Int) -> MutationAttempt<T>,
    ): MutationResult<T> {
        var attempt = 1
        while (true) {
            val outcome = block(attempt)
            when (outcome) {
                is MutationAttempt.Success -> {
                    record(spec, success = true, outcome.statusCode, outcome.requestId, attempts = attempt, message = null)
                    return MutationResult.Success(outcome.value)
                }

                is MutationAttempt.Failure -> {
                    val decision = retryPolicy.decide(attempt, outcome.error, spec.idempotent)
                    if (!decision.shouldRetry) {
                        record(
                            spec = spec,
                            success = false,
                            statusCode = outcome.statusCode,
                            requestId = outcome.requestId,
                            attempts = attempt,
                            message = outcome.error.userMessage(),
                        )
                        return MutationResult.Failure(outcome.error)
                    }
                    sleeper(decision.delayMillis)
                    attempt += 1
                }
            }
        }
    }

    @Suppress("LongParameterList")
    private suspend fun record(
        spec: MutationSpec,
        success: Boolean,
        statusCode: Int?,
        requestId: String?,
        attempts: Int,
        message: String?,
    ) {
        auditSink.record(
            AuditEntry(
                action = spec.action,
                target = spec.target,
                success = success,
                statusCode = statusCode,
                message = message,
                timestampMillis = clock(),
                attempts = attempts,
                requestId = requestId,
            ),
        )
    }
}

/** 面向用户的中文错误说明；缺权时给出可执行的重新授权指引。 */
fun MutationError.userMessage(): String =
    when (this) {
        MutationError.Unauthorized -> "登录已失效，请重新登录"
        is MutationError.Forbidden -> forbiddenMessage()
        MutationError.NotFound -> "目标不存在或无权访问"
        is MutationError.Conflict -> message ?: "服务端内容已变更，请刷新后重试"
        is MutationError.Validation -> validationMessage()
        is MutationError.RateLimited -> "API 限流，请稍后再试"
        is MutationError.Server -> "GitHub 服务异常（$status），请稍后重试"
        is MutationError.Network -> "网络错误：${cause ?: "连接失败"}"
        is MutationError.Unknown -> message ?: "未知错误（${status ?: "无状态码"}）"
    }

private fun MutationError.Forbidden.forbiddenMessage(): String =
    when (reason) {
        ForbiddenReason.MISSING_SCOPE ->
            missingScopes
                .takeIf { it.isNotEmpty() }
                ?.joinToString("、")
                ?.let { "缺少授权范围：$it。请退出登录后重新授权，或更新 PAT 的 scope" }
                ?: "当前凭证缺少此操作所需的权限，请重新授权"
        ForbiddenReason.PROTECTED_BRANCH -> "默认分支受保护，请通过 Pull Request 修改"
        ForbiddenReason.INSUFFICIENT_ROLE -> "你在该仓库的角色不足以执行此操作"
        ForbiddenReason.OTHER -> message ?: "GitHub 拒绝了此操作（403）"
    }

private fun MutationError.Validation.validationMessage(): String {
    val fields = fieldErrors.mapNotNull { it.field }.filter { it.isNotBlank() }.distinct()
    return if (fields.isEmpty()) {
        message ?: "提交内容未通过校验（422）"
    } else {
        "字段校验失败：${fields.joinToString("、")}"
    }
}
