/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.mutation.MutationResult
import com.gy.githubclient.feature.repo.domain.model.UploadTarget
import com.gy.githubclient.feature.repo.domain.usecase.UploadFileUseCase
import com.gy.githubclient.feature.repo.domain.usecase.UploadPathValidator
import com.gy.githubclient.feature.repo.domain.usecase.WriteError
import com.gy.githubclient.feature.repo.domain.usecase.toWriteError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 上传文件页状态（P-205）。 */
data class UploadFileUiState(
    val path: String = "",
    val content: String = "",
    val message: String = "",
    val submitting: Boolean = false,
    /** 路径不合法的原因（来自领域层的纯逻辑校验）。 */
    val pathError: String? = null,
    val error: WriteError? = null,
    val success: Boolean = false,
)

/**
 * 上传文件到仓库。
 *
 * 路径必须先过 [UploadPathValidator]（拒绝绝对路径、`..` 穿越、空段），
 * 否则一次上传就可能把文件写到仓库之外——这是必须在**发请求之前**拦掉的。
 */
class UploadFileViewModel(
    private val uploadFile: UploadFileUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UploadFileUiState())
    val uiState: StateFlow<UploadFileUiState> = _uiState.asStateFlow()

    fun onPathChange(value: String) {
        _uiState.value = _uiState.value.copy(path = value, pathError = null)
    }

    fun onContentChange(value: String) {
        _uiState.value = _uiState.value.copy(content = value)
    }

    fun onMessageChange(value: String) {
        _uiState.value = _uiState.value.copy(message = value)
    }

    fun submit(
        owner: String,
        name: String,
    ) {
        val current = _uiState.value
        if (current.submitting) return
        val pathResult = UploadPathValidator.validate(current.path)
        val normalized =
            when (pathResult) {
                is UploadPathValidator.Result.Valid -> pathResult.normalizedPath
                is UploadPathValidator.Result.Invalid -> {
                    _uiState.value = current.copy(pathError = pathResult.reason)
                    return
                }
            }
        viewModelScope.launch {
            _uiState.value = current.copy(submitting = true, error = null, success = false)
            val result =
                uploadFile(
                    owner = owner,
                    name = name,
                    target = UploadTarget(owner = owner, repository = name, path = normalized),
                    contentBase64 = current.content.trim(),
                    message = current.message.trim().ifBlank { "Update $normalized" },
                )
            _uiState.value =
                when (result) {
                    is MutationResult.Success -> _uiState.value.copy(submitting = false, success = true)
                    is MutationResult.Failure ->
                        _uiState.value.copy(submitting = false, error = result.error.toWriteError())
                }
        }
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
