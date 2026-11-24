/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.domain.usecase.SetStarredUseCase
import com.gy.githubclient.feature.search.domain.model.CodeHit
import com.gy.githubclient.feature.search.domain.model.CommitHit
import com.gy.githubclient.feature.search.domain.model.IssueHit
import com.gy.githubclient.feature.search.domain.model.RepoHit
import com.gy.githubclient.feature.search.domain.model.SearchHit
import com.gy.githubclient.feature.search.domain.model.SearchQuery
import com.gy.githubclient.feature.search.domain.model.SearchResult
import com.gy.githubclient.feature.search.domain.model.SearchType
import com.gy.githubclient.feature.search.domain.model.UserHit
import com.gy.githubclient.feature.search.domain.usecase.SearchUseCase
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 搜索页 UI 状态。 */
data class SearchUiState(
    val term: String = "",
    val type: SearchType = SearchType.REPOSITORY,
    val hits: List<SearchHit> = emptyList(),
    val totalCount: Int = 0,
    val isSearching: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasNextPage: Boolean = false,
    val hasSearched: Boolean = false,
    /** 本会话内已加星的仓库（fullName），用于结果卡片状态显示；GitHub PUT star 幂等，重发无害。 */
    val starredFullNames: Set<String> = emptySet(),
)

sealed interface SearchEvent {
    data class ShowMessage(
        val message: String,
    ) : SearchEvent
}

/**
 * 搜索 ViewModel：
 * - 关键字输入经 300ms 防抖 + distinctUntilChanged 后自动发起搜索。
 * - 切换类别重置结果并重新搜索；「加载更多」按页追加。
 * - 结果保存在内存（不落 Room），符合搜索时效性优先的设计。
 */
@OptIn(FlowPreview::class)
class SearchViewModel(
    private val search: SearchUseCase,
    private val setStarred: SetStarredUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    private val _events = Channel<SearchEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val termFlow = MutableStateFlow("")

    init {
        viewModelScope.launch {
            termFlow
                .debounce(DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { term -> performSearch(term) }
        }
    }

    fun onTermChange(term: String) {
        _uiState.value = _uiState.value.copy(term = term)
        termFlow.value = term
    }

    fun onTypeSelected(type: SearchType) {
        if (type == _uiState.value.type) return
        _uiState.value = _uiState.value.copy(type = type, hits = emptyList(), hasSearched = false)
        viewModelScope.launch { performSearch(_uiState.value.term) }
    }

    fun loadMore() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasNextPage || state.term.isBlank()) return
        val nextPageQuery =
            SearchQuery(
                term = state.term,
                type = state.type,
                page = (state.hits.size / PAGE_SIZE) + 1,
            )
        viewModelScope.launch {
            _uiState.value = state.copy(isLoadingMore = true)
            when (val result = search(nextPageQuery)) {
                is NetworkResult.Success -> {
                    val data = result.data
                    _uiState.value =
                        _uiState.value.copy(
                            hits = _uiState.value.hits + data.items,
                            hasNextPage = data.hasNextPage,
                            isLoadingMore = false,
                        )
                }

                is NetworkResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isLoadingMore = false)
                    _events.trySend(SearchEvent.ShowMessage(errorMessage(result.error)))
                }
            }
        }
    }

    /**
     * 收藏搜索结果中的仓库（用户主动单次动作，PUT star 幂等）。
     * 仅在「用户主动点击」时调用；不提供任何批量/定时路径以符合 GitHub AUP。
     */
    fun starRepository(hit: RepoHit) {
        val state = _uiState.value
        if (hit.fullName in state.starredFullNames) {
            _events.trySend(SearchEvent.ShowMessage("${hit.fullName} 已在本次会话中收藏"))
            return
        }
        viewModelScope.launch {
            when (val result = setStarred(hit.ownerLogin, hit.fullName.substringAfter("/"), starred = true)) {
                is NetworkResult.Success -> {
                    _uiState.value =
                        _uiState.value.copy(starredFullNames = _uiState.value.starredFullNames + hit.fullName)
                    _events.trySend(SearchEvent.ShowMessage("已收藏 ${hit.fullName}"))
                }

                is NetworkResult.Failure ->
                    _events.trySend(SearchEvent.ShowMessage(errorMessage(result.error)))
            }
        }
    }

    private suspend fun performSearch(term: String) {
        val state = _uiState.value
        if (term.isBlank()) {
            _uiState.value = state.copy(hits = emptyList(), hasSearched = false, isSearching = false)
            return
        }
        val query = SearchQuery(term = term, type = state.type, page = 1)
        _uiState.value = state.copy(isSearching = true, hasSearched = true)
        when (val result = search(query)) {
            is NetworkResult.Success -> {
                val data: SearchResult<SearchHit> = result.data
                _uiState.value =
                    _uiState.value.copy(
                        hits = data.items,
                        totalCount = data.totalCount,
                        hasNextPage = data.hasNextPage,
                        isSearching = false,
                    )
            }

            is NetworkResult.Failure -> {
                _uiState.value = _uiState.value.copy(isSearching = false, hits = emptyList())
                _events.trySend(SearchEvent.ShowMessage(errorMessage(result.error)))
            }
        }
    }

    private fun errorMessage(error: ApiError): String =
        when (error) {
            ApiError.Unauthorized -> "登录已失效，请重新登录"
            is ApiError.RateLimited -> "搜索太频繁，已触发独立限流，请稍后再试"
            ApiError.NotFound -> "未找到相关资源"
            is ApiError.Network -> "网络错误：${error.cause}"
            is ApiError.Http -> "搜索失败（${error.code}）"
            is ApiError.Unknown -> "未知错误：${error.message}"
        }

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val PAGE_SIZE = SearchQuery.DEFAULT_PER_PAGE
    }
}

/** 供 UI 展示的结果副标题。 */
fun SearchHit.subtitle(): String =
    when (this) {
        is RepoHit -> description ?: fullName
        is CodeHit -> "$repositoryFullName · $path"
        is IssueHit -> "#$number · $repositoryFullName"
        is UserHit -> accountType
        is CommitHit -> "$repositoryFullName · ${sha.take(7)}"
    }
