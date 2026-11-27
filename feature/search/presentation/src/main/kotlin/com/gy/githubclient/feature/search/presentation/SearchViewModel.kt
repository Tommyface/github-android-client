/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.search.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.link.GitHubLink
import com.gy.githubclient.core.domain.link.GitHubLinkParser
import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.core.mutation.MutationResult
import com.gy.githubclient.core.mutation.userMessage
import com.gy.githubclient.feature.profile.domain.usecase.IsFollowingUseCase
import com.gy.githubclient.feature.profile.domain.usecase.SetFollowingUseCase
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
    /** 输入框内容恰好是一条 GitHub 链接时识别出的目标（v0.16.0，ghlink 移植）。 */
    val detectedLink: GitHubLink? = null,
    /** 本会话内已加星的仓库（fullName），用于结果卡片状态显示；GitHub PUT star 幂等，重发无害。 */
    val starredFullNames: Set<String> = emptySet(),
    /** 本会话内已确认的关注状态（login -> following）；未确认的用户不在 Map 中。 */
    val followStatus: Map<String, Boolean> = emptyMap(),
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
    private val isFollowing: IsFollowingUseCase,
    private val setFollowing: SetFollowingUseCase,
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
        _uiState.value =
            _uiState.value.copy(
                term = term,
                detectedLink = GitHubLinkParser.parse(term),
            )
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

    /**
     * 关注 / 取关用户：
     * - 状态未知时先查询，已关注则只提示「再次点击可取消」，避免首次点击误取关；
     * - 状态已知时直接切换。
     */
    fun toggleFollow(hit: UserHit) {
        viewModelScope.launch {
            val known = _uiState.value.followStatus[hit.login]
            val currently =
                known ?: when (val result = isFollowing(hit.login)) {
                    is NetworkResult.Success -> result.data
                    is NetworkResult.Failure -> {
                        _events.trySend(SearchEvent.ShowMessage(errorMessage(result.error)))
                        return@launch
                    }
                }
            if (known == null && currently) {
                _uiState.value =
                    _uiState.value.copy(followStatus = _uiState.value.followStatus + (hit.login to true))
                _events.trySend(SearchEvent.ShowMessage("你已关注 ${hit.login}，再次点击可取消关注"))
                return@launch
            }
            val next = !currently
            when (val result = setFollowing(hit.login, next)) {
                is MutationResult.Success -> {
                    _uiState.value =
                        _uiState.value.copy(followStatus = _uiState.value.followStatus + (hit.login to next))
                    _events.trySend(SearchEvent.ShowMessage(if (next) "已关注 ${hit.login}" else "已取消关注 ${hit.login}"))
                }

                is MutationResult.Failure ->
                    _events.trySend(SearchEvent.ShowMessage(result.error.userMessage()))
            }
        }
    }

    private suspend fun performSearch(term: String) {
        val state = _uiState.value
        // 输入的是 GitHub 链接：展示「在应用内打开」卡片，不消耗搜索额度。
        if (term.isBlank() || GitHubLinkParser.parse(term) != null) {
            _uiState.value =
                state.copy(
                    hits = emptyList(),
                    hasSearched = false,
                    isSearching = false,
                    detectedLink = GitHubLinkParser.parse(term),
                )
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

    private fun errorMessage(error: ApiError): String = searchErrorMessage(error)

    private companion object {
        const val DEBOUNCE_MS = 300L
        const val PAGE_SIZE = SearchQuery.DEFAULT_PER_PAGE
    }
}

private const val HTTP_FORBIDDEN = 403

/** 搜索失败文案（顶层便于单测）。403 单独提示频次保护语义。 */
internal fun searchErrorMessage(error: ApiError): String =
    when (error) {
        ApiError.Unauthorized -> "登录已失效，请重新登录"
        is ApiError.RateLimited -> "搜索太频繁，已触发 GitHub 限流，请稍后再试"
        ApiError.NotFound -> "未找到相关资源"
        is ApiError.Network -> "网络错误：${error.cause}"
        is ApiError.Http ->
            if (error.code == HTTP_FORBIDDEN) {
                "搜索被 GitHub 拒绝（403）：可能触发频次保护，请稍后重试；已登录时上限为每分钟 30 次"
            } else {
                "搜索失败（${error.code}）"
            }
        is ApiError.Unknown -> "未知错误：${error.message}"
    }

/** 收藏（PUT star）失败文案：与搜索文案分离，403 直接给出权限修复指引。 */
internal fun starMessage(error: ApiError): String =
    when (error) {
        ApiError.Unauthorized -> "登录已失效，请重新登录后再收藏"
        is ApiError.RateLimited -> "操作过于频繁，已触发 GitHub 限流，请稍后再试"
        ApiError.NotFound -> "仓库不存在或已删除，无法收藏"
        is ApiError.Network -> "网络错误：${error.cause}"
        is ApiError.Http ->
            if (error.code == HTTP_FORBIDDEN) {
                "收藏失败（403）：当前凭证没有星标写权限。" +
                    "fine-grained PAT 需在 GitHub 勾选「Starring: Read and write」；" +
                    "OAuth 账号请重新授权。可到「我的 → 权限体检」核对当前凭证能力"
            } else {
                "收藏失败（${error.code}）"
            }
        is ApiError.Unknown -> "收藏失败：${error.message}"
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
