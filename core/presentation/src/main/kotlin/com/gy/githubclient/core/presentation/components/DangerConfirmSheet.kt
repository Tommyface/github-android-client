/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.gy.githubclient.core.presentation.R
import com.gy.githubclient.core.presentation.theme.LocalFrostColors

/**
 * 破坏性操作二次确认（M2-03）。
 *
 * 设计要点：
 *  - **明确后果**：正文写清"会发生什么"与"是否可撤销"，而不是只问"确定吗？"；
 *  - **方向明示**：可见性变更要说明"公开 → 私有"或反向，避免用户点错方向；
 *  - **危险色**：确认按钮用 danger 色，且默认焦点落在**取消**上；
 *  - **可再次确认**：极高危操作（删仓库）可要求输入仓库名，由 [requireTypedConfirmation] 控制。
 *
 * konsist 规则 8 要求：危险操作界面必须挂载本组件（以文件名或 import 判定）。
 */
/** 破坏性操作确认的请求内容（用数据类承载，避免长参数列表）。 */
data class DangerConfirmRequest(
    val title: String,
    /** 后果说明：必须写清会发生什么、是否可撤销。 */
    val consequences: String,
    /** 确认按钮文案；为 null 时由界面取默认文案（资源 `danger_confirm_default`）。 */
    val confirmLabel: String? = null,
    /** 需要用户输入并匹配的文本；非空时确认按钮要求逐字匹配。 */
    val requireTypedConfirmation: String? = null,
)

@Composable
fun DangerConfirmSheet(
    request: DangerConfirmRequest,
    /** 用户当前输入（用于匹配校验）。 */
    typedInput: String = "",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val frost = LocalFrostColors.current
    val confirmLabel = request.confirmLabel ?: stringResource(R.string.danger_confirm_default)
    val typedConfirmation = request.requireTypedConfirmation
    val typedOk =
        typedConfirmation == null || typedInput == typedConfirmation
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(request.title) },
        text = {
            Column {
                Text(
                    request.consequences,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (typedConfirmation != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.danger_confirm_type_prompt, typedConfirmation),
                        style = MaterialTheme.typography.labelMedium,
                        color = frost.stateWarning,
                    )
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.padding(end = 4.dp),
                ) {
                    Text(stringResource(R.string.danger_confirm_cancel))
                }
                TextButton(
                    onClick = onConfirm,
                    enabled = typedOk,
                ) {
                    Text(confirmLabel, color = frost.danger)
                }
            }
        },
        containerColor = frost.glassL3,
    )
}

/**
 * 可见性变更的方向说明（把"改成什么"讲清楚，避免方向性误操作）。
 */
@Composable
fun visibilityChangeDescription(
    currentPrivate: Boolean,
    nextPrivate: Boolean,
): String =
    if (currentPrivate && !nextPrivate) {
        stringResource(R.string.danger_visibility_private_to_public)
    } else if (!currentPrivate && nextPrivate) {
        stringResource(R.string.danger_visibility_public_to_private)
    } else {
        stringResource(R.string.danger_visibility_unchanged)
    }

/** 归档/取消归档的后果说明。 */
@Composable
fun archiveChangeDescription(currentArchived: Boolean, nextArchived: Boolean): String =
    if (currentArchived && !nextArchived) {
        stringResource(R.string.danger_archive_uncheck)
    } else if (!currentArchived && nextArchived) {
        stringResource(R.string.danger_archive_check)
    } else {
        stringResource(R.string.danger_archive_unchanged)
    }
