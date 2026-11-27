/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.gy.githubclient.feature.repo.domain.usecase.RepoWriteValidator
import com.gy.githubclient.feature.repo.domain.usecase.WriteError
import com.gy.githubclient.feature.repo.domain.usecase.WriteErrorKind

/** 写操作成功的语义分类（UI 据此选文案）。 */
enum class WriteSuccessKind {
    SETTINGS_SAVED,
    REPOSITORY_DELETED,
    REPOSITORY_CREATED,
    FILE_UPLOADED,
}

/** 写操作错误 → 用户可读文案。 */
@Composable
fun WriteError.asMessage(): String =
    when (kind) {
        WriteErrorKind.UNAUTHORIZED -> stringResource(R.string.write_error_unauthorized)
        WriteErrorKind.FORBIDDEN -> stringResource(R.string.write_error_forbidden)
        WriteErrorKind.NOT_FOUND -> stringResource(R.string.write_error_not_found)
        WriteErrorKind.CONFLICT -> stringResource(R.string.write_error_conflict)
        WriteErrorKind.VALIDATION -> stringResource(R.string.write_error_validation)
        WriteErrorKind.RATE_LIMITED -> stringResource(R.string.write_error_rate_limited)
        WriteErrorKind.SERVER ->
            status?.let { code -> stringResource(R.string.write_error_server, code) }
                ?: stringResource(R.string.write_error_unknown, kind.name)

        WriteErrorKind.NETWORK -> stringResource(R.string.write_error_network)
        WriteErrorKind.UNKNOWN -> stringResource(R.string.write_error_unknown, kind.name)
    }

/** 写操作错误的补充说明（如缺失的 scope、字段错误）。 */
@Composable
fun WriteError.detailOrNull(): String? = detail?.let { stringResource(R.string.write_error_detail, it) }

/** 校验失败原因 → 用户可读文案。 */
@Composable
fun RepoWriteValidator.InvalidReason.asMessage(): String =
    when (this) {
        RepoWriteValidator.InvalidReason.NAME_EMPTY -> stringResource(R.string.write_invalid_name_empty)
        RepoWriteValidator.InvalidReason.NAME_TOO_LONG -> stringResource(R.string.write_invalid_name_too_long)
        RepoWriteValidator.InvalidReason.NAME_INVALID_CHARS -> stringResource(R.string.write_invalid_name_chars)
        RepoWriteValidator.InvalidReason.DESCRIPTION_TOO_LONG ->
            stringResource(R.string.write_invalid_description_too_long)
    }
