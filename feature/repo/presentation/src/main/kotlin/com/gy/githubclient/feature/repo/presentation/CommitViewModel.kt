/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.domain.model.CommitDetail
import com.gy.githubclient.feature.repo.domain.model.CommitSummary
import com.gy.githubclient.feature.repo.domain.usecase.GetCommitDetailUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListCommitsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 提交历史 + 单次提交详情 UI 状态（v0.12.0 N10）。 */
data class CommitUiState(
    val commits: List<CommitSummary> = emptyList(),
    val detail: CommitDetail? = null,
    val page: Int = 1,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val error: String? = null,
)

/** 提交历史 ViewModel：列表分页 + 打开单次提交详情。 */
class CommitViewModel(
    private val listCommits: ListCommitsUseCase,
    private val getCommitDetail: GetCommitDetailUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CommitUiState())
    val uiState: StateFlow<CommitUiState> = _uiState.asStateFlow()

    private var owner: String = ""
    private var name: String = ""

    fun init(
        owner: String,
        name: String,
    ) {
        this.owner = owner
        this.name = name
        _uiState.value = CommitUiState()
        load(page = 1, append = false)
    }

    fun loadNextPage() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingMore || !state.hasMore) return
        load(page = state.page + 1, append = true)
    }

    fun openDetail(sha: String) {
        if (sha.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = getCommitDetail(owner, name, sha)) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(detail = result.data, isLoading = false)
                is NetworkResult.Failure ->
                    _uiState.value =
                        _uiState.value.copy(detail = null, isLoading = false, error = "提交详情加载失败：${result.error}")
            }
        }
    }

    fun closeDetail() {
        _uiState.value = _uiState.value.copy(detail = null)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    private fun load(
        page: Int,
        append: Boolean,
    ) {
        if (owner.isBlank() || name.isBlank()) return
        viewModelScope.launch {
            _uiState.value =
                if (append) _uiState.value.copy(isLoadingMore = true, error = null) else _uiState.value.copy(isLoading = true, error = null)
            when (val result = listCommits(owner, name, page)) {
                is NetworkResult.Success -> {
                    val items = result.data
                    _uiState.value =
                        _uiState.value.copy(
                            commits = if (append) _uiState.value.commits + items else items,
                            page = page,
                            hasMore = items.isNotEmpty(),
                            isLoading = false,
                            isLoadingMore = false,
                        )
                }

                is NetworkResult.Failure ->
                    _uiState.value =
                        _uiState.value.copy(
                            isLoading = false,
                            isLoadingMore = false,
                            error = "提交历史加载失败：${result.error}",
                        )
            }
        }
    }
}
