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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.androidx.compose.koinViewModel

/**
 * 上传文件页（P-205）。
 *
 * 路径先过领域层的 [com.gy.githubclient.feature.repo.domain.usecase.UploadPathValidator]：
 * 拒绝绝对路径、`..` 穿越与空段——否则一次上传就可能写到仓库之外。
 */
@Composable
fun UploadFileScreen(
    owner: String,
    name: String,
    onBack: () -> Unit,
    viewModel: UploadFileViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
            Text(stringResource(R.string.upload_file_title), style = MaterialTheme.typography.titleLarge)
        }
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.path,
            onValueChange = viewModel::onPathChange,
            label = { Text(stringResource(R.string.upload_file_path_label)) },
            placeholder = { Text(stringResource(R.string.upload_file_path_hint)) },
            singleLine = true,
            isError = uiState.pathError != null,
            supportingText = { uiState.pathError?.let { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.content,
            onValueChange = viewModel::onContentChange,
            label = { Text(stringResource(R.string.upload_file_content_label)) },
            placeholder = { Text(stringResource(R.string.upload_file_content_hint)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.message,
            onValueChange = viewModel::onMessageChange,
            label = { Text(stringResource(R.string.upload_file_message_label)) },
            placeholder = { Text(stringResource(R.string.upload_file_message_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { viewModel.submit(owner, name) },
            enabled = !uiState.submitting,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.upload_file_submit))
        }

        uiState.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(error.asMessage(), color = MaterialTheme.colorScheme.error)
            error.detailOrNull()?.let { detail ->
                Text(detail, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (uiState.success) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.upload_file_done), color = MaterialTheme.colorScheme.primary)
        }
    }
}
