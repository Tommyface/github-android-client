/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
@Composable
fun DangerConfirmSheet(
    title: String,
    /** 后果说明：必须写清会发生什么、是否可撤销。 */
    consequences: String,
    /** 确认按钮文案（默认"确认"）。 */
    confirmLabel: String = "确认",
    /** 需要用户输入并匹配的文本；非空时确认按钮要求逐字匹配。 */
    requireTypedConfirmation: String? = null,
    /** 用户当前输入（用于匹配校验）。 */
    typedInput: String = "",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val frost = LocalFrostColors.current
    val typedOk = requireTypedConfirmation == null || typedInput == requireTypedConfirmation
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(
                    consequences,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (requireTypedConfirmation != null) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "请输入 $requireTypedConfirmation 以确认",
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
                    Text("取消")
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
fun visibilityChangeDescription(
    currentPrivate: Boolean,
    nextPrivate: Boolean,
): String =
    if (currentPrivate && !nextPrivate) {
        "仓库将从【私有】变为【公开】：任何人都能查看与克隆该仓库，此变更可随时改回。"
    } else if (!currentPrivate && nextPrivate) {
        "仓库将从【公开】变为【私有】：外部用户将无法访问该仓库，此变更可随时改回。"
    } else {
        "可见性保持不变。"
    }

/** 归档/取消归档的后果说明。 */
fun archiveChangeDescription(currentArchived: Boolean, nextArchived: Boolean): String =
    if (currentArchived && !nextArchived) {
        "取消归档后仓库将恢复可读写状态。"
    } else if (!currentArchived && nextArchived) {
        "归档后仓库将变为只读：不能再提交、推送或新建 Issue/Release，但内容仍可访问。"
    } else {
        "归档状态保持不变。"
    }
