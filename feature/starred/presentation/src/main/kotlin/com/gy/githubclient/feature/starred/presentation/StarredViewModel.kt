/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.starred.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.domain.model.Repository
import com.gy.githubclient.feature.starred.domain.model.StarredFilter
import com.gy.githubclient.feature.starred.domain.model.StarredPage
import com.gy.githubclient.feature.starred.domain.model.StarredSort
import com.gy.githubclient.feature.starred.domain.usecase.ObserveStarredUseCase
import com.gy.githubclient.feature.starred.domain.usecase.RefreshStarredUseCase
import com.gy.githubclient.feature.starred.domain.usecase.RemoveStarUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 星标列表 UI 状态。 */
data class StarredUiState(
    val filter: StarredFilter = StarredFilter(),
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = true,
)

/** 一次性事件。 */
sealed interface StarredEvent {
    data class ShowMessage(
        val message: String,
    ) : StarredEvent
}

/** 星标仓库 ViewModel：观察缓存 + 刷新/分页/移除星标。 */
class StarredViewModel(
    private val observeStarred: ObserveStarredUseCase,
    private val refreshStarred: RefreshStarredUseCase,
    private val removeStar: RemoveStarUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(StarredUiState())
    val uiState: StateFlow<StarredUiState> = _uiState.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val starredRepos: StateFlow<List<Repository>> =
        observeStarred()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
                initialValue = emptyList(),
            )

    private val _events = Channel<StarredEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        refresh()
    }

    fun onSortSelected(sort: StarredSort) {
        _uiState.value = _uiState.value.copy(filter = StarredFilter(sort = sort))
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            val filter = _uiState.value.filter.copy(page = 1)
            when (val result = refreshStarred(filter, append = false)) {
                is NetworkResult.Success -> handlePage(result.data, reset = true)
                is NetworkResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isRefreshing = false)
                    _events.trySend(StarredEvent.ShowMessage(errorMessage(result.error)))
                }
            }
        }
    }

    fun loadMore() {
        if (!_uiState.value.hasMore) return
        viewModelScope.launch {
            val nextFilter = _uiState.value.filter.nextPage()
            _uiState.value = _uiState.value.copy(isLoadingMore = true)
            when (val result = refreshStarred(nextFilter, append = true)) {
                is NetworkResult.Success -> handlePage(result.data, reset = false, nextFilter = nextFilter)
                is NetworkResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                    _events.trySend(StarredEvent.ShowMessage(errorMessage(result.error)))
                }
            }
        }
    }

    fun removeStar(repo: Repository) {
        viewModelScope.launch {
            when (val result = removeStar(repo.owner.login, repo.name)) {
                is NetworkResult.Success ->
                    _events.trySend(StarredEvent.ShowMessage("已取消 Star ${repo.fullName}"))

                is NetworkResult.Failure -> _events.trySend(StarredEvent.ShowMessage(errorMessage(result.error)))
            }
        }
    }

    private fun handlePage(
        page: StarredPage,
        reset: Boolean,
        nextFilter: StarredFilter = _uiState.value.filter,
    ) {
        _uiState.value =
            _uiState.value.copy(
                filter = if (reset) _uiState.value.filter.copy(page = 1) else nextFilter,
                isRefreshing = false,
                isLoadingMore = false,
                hasMore = page.hasMore,
            )
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
