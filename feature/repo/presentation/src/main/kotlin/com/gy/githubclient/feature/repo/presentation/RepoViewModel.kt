/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.domain.model.Branch
import com.gy.githubclient.feature.repo.domain.model.RepoFilter
import com.gy.githubclient.feature.repo.domain.model.RepoType
import com.gy.githubclient.feature.repo.domain.model.Repository
import com.gy.githubclient.feature.repo.domain.usecase.GetRepositoryUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListBranchesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ObserveRepositoriesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.RefreshRepositoriesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.SetStarredUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 仓库列表 UI 状态。 */
data class RepoListUiState(
    val filter: RepoFilter = RepoFilter(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val query: String = "",
)

/** 一次性事件。 */
sealed interface RepoEvent {
    data class ShowMessage(
        val message: String,
    ) : RepoEvent

    data object OpenDetail : RepoEvent
}

/**
 * 仓库列表 ViewModel：
 * - [repositories] 由 filter 驱动观察 Room 缓存，切换筛选条件自动换流。
 * - 刷新/加 star 走网络，失败以事件提示，缓存数据始终保持可见。
 */
class RepoViewModel(
    private val observeRepositories: ObserveRepositoriesUseCase,
    private val refreshRepositories: RefreshRepositoriesUseCase,
    private val setStarred: SetStarredUseCase,
    private val getRepository: GetRepositoryUseCase,
    private val listBranches: ListBranchesUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RepoListUiState())
    val uiState: StateFlow<RepoListUiState> = _uiState.asStateFlow()

    /** 当前筛选下的仓库缓存列表（SSOT）。 */
    @OptIn(ExperimentalCoroutinesApi::class)
    val repositories: StateFlow<List<Repository>> =
        _uiState
            .flatMapLatest { state ->
                val flow = observeRepositories(state.filter)
                if (state.query.isBlank()) {
                    flow
                } else {
                    // 简单内存过滤；查询关键字仅作用于当前已缓存列表。
                    flow.map { list ->
                        list.filter { repo ->
                            repo.name.contains(state.query, ignoreCase = true) ||
                                repo.fullName.contains(state.query, ignoreCase = true) ||
                                repo.description?.contains(state.query, ignoreCase = true) == true
                        }
                    }
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList(),
            )

    private val _events = Channel<RepoEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    /** 详情页状态。 */
    private val _detail = MutableStateFlow<Repository?>(null)
    val detail: StateFlow<Repository?> = _detail.asStateFlow()

    private val _branches = MutableStateFlow<List<Branch>>(emptyList())
    val branches: StateFlow<List<Branch>> = _branches.asStateFlow()

    init {
        refresh()
    }

    fun onTypeSelected(type: RepoType) {
        _uiState.value = _uiState.value.copy(filter = _uiState.value.filter.copy(type = type))
        refresh()
    }

    fun onQueryChange(query: String) {
        _uiState.value = _uiState.value.copy(query = query)
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            val result = refreshRepositories(_uiState.value.filter, append = false)
            _uiState.value = _uiState.value.copy(isRefreshing = false)
            handleCountResult(result)
        }
    }

    fun loadMore() {
        viewModelScope.launch {
            val nextFilter = _uiState.value.filter.nextPage()
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            val result = refreshRepositories(nextFilter, append = true)
            _uiState.value = _uiState.value.copy(isLoadingMore = false)
            when (result) {
                is NetworkResult.Success -> {
                    if (result.data > 0) {
                        _uiState.value = _uiState.value.copy(filter = nextFilter)
                    }
                }

                is NetworkResult.Failure -> _events.trySend(RepoEvent.ShowMessage(errorMessage(result.error)))
            }
        }
    }

    fun openDetail(owner: String, name: String) {
        viewModelScope.launch {
            when (val result = getRepository(owner, name)) {
                is NetworkResult.Success -> {
                    _detail.value = result.data
                    _events.trySend(RepoEvent.OpenDetail)
                    loadBranches(owner, name)
                }

                is NetworkResult.Failure -> _events.trySend(RepoEvent.ShowMessage(errorMessage(result.error)))
            }
        }
    }

    fun toggleStar(repo: Repository) {
        viewModelScope.launch {
            val next = !repo.isStarred
            when (val result = setStarred(repo.owner.login, repo.name, next)) {
                is NetworkResult.Success ->
                    _events.trySend(
                        RepoEvent.ShowMessage(if (next) "已 Star ${repo.fullName}" else "已取消 Star ${repo.fullName}"),
                    )

                is NetworkResult.Failure -> _events.trySend(RepoEvent.ShowMessage(errorMessage(result.error)))
            }
        }
    }

    private suspend fun loadBranches(owner: String, name: String) {
        when (val result = listBranches(owner, name)) {
            is NetworkResult.Success -> _branches.value = result.data
            is NetworkResult.Failure -> _branches.value = emptyList()
        }
    }

    private fun handleCountResult(result: NetworkResult<Int>) {
        if (result is NetworkResult.Failure) {
            _events.trySend(RepoEvent.ShowMessage(errorMessage(result.error)))
        }
    }

    private fun errorMessage(error: ApiError): String =
        when (error) {
            ApiError.Unauthorized -> "登录已失效，请重新登录"
            is ApiError.RateLimited -> "API 限流，请稍后再试"
            ApiError.NotFound -> "资源不存在"
            is ApiError.Network -> "网络错误：${error.cause}"
            is ApiError.Http -> "请求失败（${error.code}）"
            is ApiError.Unknown -> "未知错误：${error.message}"
        }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
