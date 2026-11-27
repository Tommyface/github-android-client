/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain.usecase

import com.gy.githubclient.core.mutation.MutationError

/**
 * 写操作失败的**语义分类**（P-205）。
 *
 * 为什么不让 domain 直接给中文文案：
 *  - domain 是纯逻辑层，文案属于界面层的事（要能本地化、要能按语言变化）；
 *  - HTTP 细节（状态码、字段错误）不该原样漏给用户，但也不能丢——需要排查时还要用。
 *
 * 因此：domain 只给出**分类 + 细节**，界面层用分类去选文案，细节用于「详情」区域。
 */
enum class WriteErrorKind {
    UNAUTHORIZED,
    FORBIDDEN,
    NOT_FOUND,
    CONFLICT,
    VALIDATION,
    RATE_LIMITED,
    SERVER,
    NETWORK,
    UNKNOWN,
}

/** 写操作错误（分类 + 可选细节）。 */
data class WriteError(
    val kind: WriteErrorKind,
    /** HTTP 状态码（服务端错误与未知错误时可能有）。 */
    val status: Int? = null,
    /** 补充说明（如缺失的 scope、字段错误、限流恢复时间）。 */
    val detail: String? = null,
)

/** 把数据层的 [MutationError] 归一化成界面可消费的 [WriteError]。 */
fun MutationError.toWriteError(): WriteError =
    when (this) {
        is MutationError.Unauthorized -> WriteError(WriteErrorKind.UNAUTHORIZED)

        is MutationError.Forbidden -> WriteError(WriteErrorKind.FORBIDDEN, detail = forbiddenDetail())

        is MutationError.NotFound -> WriteError(WriteErrorKind.NOT_FOUND)

        is MutationError.Conflict -> WriteError(WriteErrorKind.CONFLICT, detail = message)

        is MutationError.Validation -> WriteError(WriteErrorKind.VALIDATION, detail = validationDetail())

        is MutationError.RateLimited ->
            WriteError(WriteErrorKind.RATE_LIMITED, detail = retryAfterSeconds?.toString())

        is MutationError.Server -> WriteError(WriteErrorKind.SERVER, status = status, detail = message)

        is MutationError.Network -> WriteError(WriteErrorKind.NETWORK, detail = cause)

        is MutationError.Unknown -> WriteError(WriteErrorKind.UNKNOWN, status = status, detail = message)
    }

/** 403 的补充说明：优先给出缺失的 scope（用户知道该去补哪个权限），其次是服务端消息。 */
private fun MutationError.Forbidden.forbiddenDetail(): String? =
    when {
        missingScopes.isNotEmpty() -> missingScopes.joinToString(", ")
        message != null -> message
        else -> null
    }

/** 422 的补充说明：优先整体消息，否则把字段级错误拼成一行。 */
private fun MutationError.Validation.validationDetail(): String? =
    message
        ?: fieldErrors
            .takeIf { it.isNotEmpty() }
            ?.joinToString("; ") { error ->
                val text = error.message ?: error.code
                "${error.field}: $text"
            }

/**
 * 仓库名 / 简介的**纯逻辑校验**（P-205，可单测）。
 *
 * 规则与 GitHub 一致：
 *  - 仓库名不能为空、不能超过 100 个字符；
 *  - 仓库名只允许字母、数字、`.`、`-`、`_`；
 *  - 简介不超过 350 个字符。
 *
 * 校验结果只给**原因码**，文案由界面层映射（保持 domain 无文案）。
 */
object RepoWriteValidator {
    const val NAME_MAX_LENGTH = 100
    const val DESCRIPTION_MAX_LENGTH = 350

    /** 校验失败的原因码。 */
    enum class InvalidReason {
        NAME_EMPTY,
        NAME_TOO_LONG,
        NAME_INVALID_CHARS,
        DESCRIPTION_TOO_LONG,
    }

    /** 校验结果。 */
    sealed interface Result {
        data object Valid : Result

        data class Invalid(
            val reason: InvalidReason,
        ) : Result
    }

    private val NAME_PATTERN = Regex("^[A-Za-z0-9._-]+$")

    fun validateName(name: String): Result {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return Result.Invalid(InvalidReason.NAME_EMPTY)
        if (trimmed.length > NAME_MAX_LENGTH) return Result.Invalid(InvalidReason.NAME_TOO_LONG)
        if (!NAME_PATTERN.matches(trimmed)) return Result.Invalid(InvalidReason.NAME_INVALID_CHARS)
        return Result.Valid
    }

    fun validateDescription(description: String?): Result =
        if ((description?.length ?: 0) > DESCRIPTION_MAX_LENGTH) {
            Result.Invalid(InvalidReason.DESCRIPTION_TOO_LONG)
        } else {
            Result.Valid
        }
}
