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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * 新建仓库页（P-205）。
 *
 * 创建**不可幂等**（失败不自动重试），因此按钮在提交期间禁用，避免重复建库。
 */
@Composable
fun CreateRepositoryScreen(
    onBack: () -> Unit,
    onCreated: (String) -> Unit,
    viewModel: CreateRepositoryViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.createdFullName) {
        uiState.createdFullName?.let(onCreated)
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
            Text(stringResource(R.string.create_repo_title), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.name,
            onValueChange = viewModel::onNameChange,
            label = { Text(stringResource(R.string.create_repo_name_label)) },
            placeholder = { Text(stringResource(R.string.create_repo_name_hint)) },
            singleLine = true,
            isError = uiState.nameError != null,
            supportingText = {
                uiState.nameError?.let { reason -> Text(reason.asMessage()) }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.description,
            onValueChange = viewModel::onDescriptionChange,
            label = { Text(stringResource(R.string.create_repo_description_label)) },
            isError = uiState.descriptionError != null,
            supportingText = {
                uiState.descriptionError?.let { reason -> Text(reason.asMessage()) }
            },
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
                stringResource(R.string.create_repo_private_label),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = viewModel::submit,
            enabled = !uiState.submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.create_repo_submit))
        }

        uiState.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(error.asMessage(), color = MaterialTheme.colorScheme.error)
            error.detailOrNull()?.let { detail ->
                Text(detail, style = MaterialTheme.typography.labelSmall)
            }
        }
        uiState.createdFullName?.let { fullName ->
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.create_repo_created, fullName),
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
