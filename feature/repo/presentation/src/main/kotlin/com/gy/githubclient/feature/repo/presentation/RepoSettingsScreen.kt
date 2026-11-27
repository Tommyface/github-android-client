/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.core.presentation.components.DangerConfirmRequest
import com.gy.githubclient.core.presentation.components.DangerConfirmSheet
import org.koin.androidx.compose.koinViewModel

/**
 * 仓库设置页（P-205）：简介 / 可见性 / 归档 / 删除。
 *
 * 危险操作纪律：
 *  - 可见性与归档是**方向性变更**，确认弹窗写清"会变成什么"；
 *  - 删除仓库**不可撤销**，要求用户逐字输入仓库名才允许继续。
 */
@Composable
fun RepoSettingsScreen(
    owner: String,
    name: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: RepoSettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }
    var typedInput by remember { mutableStateOf("") }

    LaunchedEffect(owner, name) { viewModel.load(owner, name) }

    LaunchedEffect(uiState.success) {
        if (uiState.success == WriteSuccessKind.REPOSITORY_DELETED) {
            viewModel.consumeSuccess()
            onDeleted()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
            Text(stringResource(R.string.repo_settings_title), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))

        if (uiState.loading) {
            CircularProgressIndicator()
        }
        if (uiState.loadFailed) {
            Text(
                stringResource(R.string.repo_settings_load_failed),
                color = MaterialTheme.colorScheme.error,
            )
        }

        OutlinedTextField(
            value = uiState.description,
            onValueChange = viewModel::onDescriptionChange,
            label = { Text(stringResource(R.string.repo_settings_description_label)) },
            placeholder = { Text(stringResource(R.string.repo_settings_description_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Switch(checked = uiState.isPrivate, onCheckedChange = viewModel::onPrivateChange)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.repo_settings_visibility_label) +
                    "：" +
                    stringResource(
                        if (uiState.isPrivate) {
                            R.string.repo_settings_visibility_private
                        } else {
                            R.string.repo_settings_visibility_public
                        },
                    ),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Switch(checked = uiState.isArchived, onCheckedChange = viewModel::onArchivedChange)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.repo_settings_archived_label),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            stringResource(R.string.repo_settings_archived_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { viewModel.save(owner, name) },
            enabled = !uiState.saving && !uiState.deleting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.repo_settings_save))
        }
        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { confirmDelete = true },
            enabled = !uiState.saving && !uiState.deleting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.repo_settings_delete), color = MaterialTheme.colorScheme.error)
        }

        if (uiState.saving || uiState.deleting) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.write_working), style = MaterialTheme.typography.labelSmall)
        }

        uiState.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(error.asMessage(), color = MaterialTheme.colorScheme.error)
            error.detailOrNull()?.let { detail ->
                Text(detail, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (uiState.success == WriteSuccessKind.SETTINGS_SAVED) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.repo_settings_saved), color = MaterialTheme.colorScheme.primary)
        }
    }

    if (confirmDelete) {
        DangerConfirmSheet(
            request =
                DangerConfirmRequest(
                    title = stringResource(R.string.repo_settings_delete_title),
                    consequences = stringResource(R.string.repo_settings_delete_consequences),
                    confirmLabel = stringResource(R.string.repo_settings_delete_confirm),
                    requireTypedConfirmation = name,
                ),
            typedInput = typedInput,
            onConfirm = {
                confirmDelete = false
                typedInput = ""
                viewModel.delete(owner, name)
            },
            onDismiss = {
                confirmDelete = false
                typedInput = ""
            },
        )
    }
}
