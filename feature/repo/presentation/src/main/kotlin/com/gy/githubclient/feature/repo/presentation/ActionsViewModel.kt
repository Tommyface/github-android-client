/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.domain.model.WorkflowRun
import com.gy.githubclient.feature.repo.domain.usecase.ListWorkflowRunsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Actions Run 列表 UI 状态（v0.14.0 A3）。 */
data class ActionsUiState(
    val runs: List<WorkflowRun> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val page: Int = 1,
    val error: String? = null,
)

/** Actions workflow run 只读 ViewModel（v0.14.0 A3）。 */
class ActionsViewModel(
    private val listWorkflowRuns: ListWorkflowRunsUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ActionsUiState())
    val uiState: StateFlow<ActionsUiState> = _uiState.asStateFlow()

    private var owner = ""
    private var name = ""

    fun init(
        owner: String,
        name: String,
    ) {
        this.owner = owner
        this.name = name
        _uiState.value = ActionsUiState()
        load(page = 1, append = false)
    }

    fun loadMore() {
        if (_uiState.value.isLoadingMore || !_uiState.value.hasMore) return
        load(page = _uiState.value.page + 1, append = true)
    }

    private fun load(
        page: Int,
        append: Boolean,
    ) {
        if (owner.isBlank() || name.isBlank()) return
        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(
                    isLoading = !append,
                    isLoadingMore = append,
                    error = null,
                )
            when (val result = listWorkflowRuns(owner, name, page, PER_PAGE)) {
                is NetworkResult.Success -> {
                    val data = result.data
                    _uiState.value =
                        _uiState.value.copy(
                            runs = if (append) _uiState.value.runs + data.runs else data.runs,
                            hasMore = data.hasMore,
                            page = page,
                            isLoading = false,
                            isLoadingMore = false,
                        )
                }

                is NetworkResult.Failure ->
                    _uiState.value =
                        _uiState.value.copy(
                            error = "Actions 列表加载失败：${result.error}",
                            isLoading = false,
                            isLoadingMore = false,
                        )
            }
        }
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private companion object {
        const val PER_PAGE = 30
    }
}
