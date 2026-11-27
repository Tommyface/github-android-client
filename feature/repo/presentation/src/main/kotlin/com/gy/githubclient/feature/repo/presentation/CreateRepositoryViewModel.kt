/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.mutation.MutationResult
import com.gy.githubclient.feature.repo.domain.usecase.CreateRepositoryUseCase
import com.gy.githubclient.feature.repo.domain.usecase.RepoWriteValidator
import com.gy.githubclient.feature.repo.domain.usecase.WriteError
import com.gy.githubclient.feature.repo.domain.usecase.toWriteError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 新建仓库页状态（P-205）。 */
data class CreateRepositoryUiState(
    val name: String = "",
    val description: String = "",
    val isPrivate: Boolean = false,
    val submitting: Boolean = false,
    val nameError: RepoWriteValidator.InvalidReason? = null,
    val descriptionError: RepoWriteValidator.InvalidReason? = null,
    val error: WriteError? = null,
    val createdFullName: String? = null,
)

/**
 * 新建仓库。
 *
 * 校验在**提交前**做（名称与简介的规则见 [RepoWriteValidator]），
 * 避免把明显非法的请求发给 GitHub 再拿 422 回来。
 */
class CreateRepositoryViewModel(
    private val createRepository: CreateRepositoryUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CreateRepositoryUiState())
    val uiState: StateFlow<CreateRepositoryUiState> = _uiState.asStateFlow()

    fun onNameChange(value: String) {
        _uiState.value = _uiState.value.copy(name = value, nameError = null)
    }

    fun onDescriptionChange(value: String) {
        _uiState.value = _uiState.value.copy(description = value, descriptionError = null)
    }

    fun onPrivateChange(value: Boolean) {
        _uiState.value = _uiState.value.copy(isPrivate = value)
    }

    fun submit() {
        val current = _uiState.value
        if (current.submitting) return
        val nameResult = RepoWriteValidator.validateName(current.name)
        val descriptionResult = RepoWriteValidator.validateDescription(current.description)
        val nameError = (nameResult as? RepoWriteValidator.Result.Invalid)?.reason
        val descriptionError = (descriptionResult as? RepoWriteValidator.Result.Invalid)?.reason
        if (nameError != null || descriptionError != null) {
            _uiState.value =
                current.copy(
                    nameError = nameError,
                    descriptionError = descriptionError,
                )
            return
        }
        viewModelScope.launch {
            _uiState.value = current.copy(submitting = true, error = null)
            val result =
                createRepository(
                    name = current.name.trim(),
                    description = current.description.trim().ifBlank { null },
                    private = current.isPrivate,
                )
            _uiState.value =
                when (result) {
                    is MutationResult.Success ->
                        _uiState.value.copy(
                            submitting = false,
                            createdFullName = result.value.fullName,
                        )

                    is MutationResult.Failure ->
                        _uiState.value.copy(submitting = false, error = result.error.toWriteError())
                }
        }
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
