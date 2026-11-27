/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.core.mutation.MutationResult
import com.gy.githubclient.feature.repo.domain.usecase.DeleteRepositoryUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetRepositoryUseCase
import com.gy.githubclient.feature.repo.domain.usecase.UpdateRepoSettingsUseCase
import com.gy.githubclient.feature.repo.domain.usecase.WriteError
import com.gy.githubclient.feature.repo.domain.usecase.toWriteError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 仓库设置页状态（P-205）。 */
data class RepoSettingsUiState(
    val loading: Boolean = false,
    val saving: Boolean = false,
    val deleting: Boolean = false,
    val loaded: Boolean = false,
    val loadFailed: Boolean = false,
    val description: String = "",
    val isPrivate: Boolean = false,
    val isArchived: Boolean = false,
    val error: WriteError? = null,
    val success: WriteSuccessKind? = null,
)

/**
 * 仓库设置：读取当前设置 → 修改简介 / 可见性 / 归档 → 删除仓库。
 *
 * 纪律：
 *  - 读与写**分开**：读走 [GetRepositoryUseCase]，写走 mutation 通道（统一信封 + 审计）；
 *  - 可见性与归档属于危险操作，**由界面层挂二次确认**（本 VM 不负责弹窗）；
 *  - 删除仓库必须由界面层校验"用户输入的仓库名与目标一致"后才调用 [deleteRepository]。
 */
class RepoSettingsViewModel(
    private val getRepository: GetRepositoryUseCase,
    private val updateSettings: UpdateRepoSettingsUseCase,
    private val deleteRepository: DeleteRepositoryUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RepoSettingsUiState())
    val uiState: StateFlow<RepoSettingsUiState> = _uiState.asStateFlow()

    fun load(
        owner: String,
        name: String,
    ) {
        if (owner.isBlank() || name.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, loadFailed = false, error = null)
            when (val result = getRepository(owner, name)) {
                is NetworkResult.Success ->
                    _uiState.value =
                        _uiState.value.copy(
                            loading = false,
                            loaded = true,
                            description = result.data.description.orEmpty(),
                            isPrivate = result.data.isPrivate,
                            isArchived = result.data.isArchived,
                        )

                is NetworkResult.Failure ->
                    _uiState.value = _uiState.value.copy(loading = false, loadFailed = true)
            }
        }
    }

    fun onDescriptionChange(value: String) {
        _uiState.value = _uiState.value.copy(description = value)
    }

    fun onPrivateChange(value: Boolean) {
        _uiState.value = _uiState.value.copy(isPrivate = value)
    }

    fun onArchivedChange(value: Boolean) {
        _uiState.value = _uiState.value.copy(isArchived = value)
    }

    /** 保存设置（简介 / 可见性 / 归档）。 */
    fun save(
        owner: String,
        name: String,
    ) {
        if (_uiState.value.saving) return
        val current = _uiState.value
        viewModelScope.launch {
            _uiState.value = current.copy(saving = true, error = null, success = null)
            val result =
                updateSettings(
                    owner = owner,
                    name = name,
                    description = current.description.trim().ifBlank { null },
                    private = current.isPrivate,
                    archived = current.isArchived,
                )
            _uiState.value =
                when (result) {
                    is MutationResult.Success ->
                        _uiState.value.copy(saving = false, success = WriteSuccessKind.SETTINGS_SAVED)

                    is MutationResult.Failure ->
                        _uiState.value.copy(saving = false, error = result.error.toWriteError())
                }
        }
    }

    /** 删除仓库（不可撤销）。调用前界面必须已完成"输入仓库名"的一致性校验。 */
    fun delete(
        owner: String,
        name: String,
    ) {
        if (_uiState.value.deleting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(deleting = true, error = null, success = null)
            val result = deleteRepository(owner, name)
            _uiState.value =
                when (result) {
                    is MutationResult.Success ->
                        _uiState.value.copy(deleting = false, success = WriteSuccessKind.REPOSITORY_DELETED)

                    is MutationResult.Failure ->
                        _uiState.value.copy(deleting = false, error = result.error.toWriteError())
                }
        }
    }

    fun consumeError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun consumeSuccess() {
        _uiState.value = _uiState.value.copy(success = null)
    }
}
