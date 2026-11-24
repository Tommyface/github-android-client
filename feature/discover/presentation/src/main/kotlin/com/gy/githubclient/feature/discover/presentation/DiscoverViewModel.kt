/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.core.domain.share.ShareManager
import com.gy.githubclient.feature.discover.domain.model.DiscoverCategory
import com.gy.githubclient.feature.discover.domain.model.DiscoverItem
import com.gy.githubclient.feature.discover.domain.model.MirrorDownloadLink
import com.gy.githubclient.feature.discover.domain.model.RankQueryFactory
import com.gy.githubclient.feature.discover.domain.model.ReleaseAsset
import com.gy.githubclient.feature.discover.domain.usecase.BuildShareTextUseCase
import com.gy.githubclient.feature.discover.domain.usecase.ObserveDiscoverUseCase
import com.gy.githubclient.feature.discover.domain.usecase.RefreshDiscoverUseCase
import com.gy.githubclient.feature.discover.domain.usecase.ResolveDownloadLinkUseCase
import com.gy.githubclient.feature.download.domain.usecase.EnqueueDownloadUseCase
import com.gy.githubclient.feature.search.domain.model.RepoHit
import com.gy.githubclient.feature.search.domain.model.SearchOrder
import com.gy.githubclient.feature.search.domain.model.SearchQuery
import com.gy.githubclient.feature.search.domain.model.SearchType
import com.gy.githubclient.feature.search.domain.usecase.SearchUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 榜单类型：精选 / 趋势（v0.11.0 N6）/ 新发布 / 热门。 */
enum class RankTab {
    FEATURED,
    TRENDING,
    NEW,
    HOT,
}

/** 发现页 UI 状态。 */
data class DiscoverUiState(
    val allItems: List<DiscoverItem> = emptyList(),
    val category: DiscoverCategory = DiscoverCategory.ALL,
    val isRefreshing: Boolean = false,
    val selected: DiscoverItem? = null,
    val rankTab: RankTab = RankTab.FEATURED,
    val rankItems: List<RepoHit> = emptyList(),
    val isRankLoading: Boolean = false,
    /** v0.11.0 N6：语言筛选，取值为 RankQueryFactory.LANGUAGES 之一。 */
    val rankLanguage: String = RankQueryFactory.LANGUAGES.first(),
    /** 榜单查询口径说明（非官方近似标注），随 tab 变化。 */
    val rankNote: String = "",
) {
    /** 按当前分类筛选后的可见列表（派生，不额外存储）。 */
    val items: List<DiscoverItem>
        get() =
            if (category == DiscoverCategory.ALL) {
                allItems
            } else {
                allItems.filter { it.featured.category == category }
            }
}

/** 一次性事件：跳转、分享、提示。 */
sealed interface DiscoverEvent {
    data class OpenRelease(
        val owner: String,
        val name: String,
    ) : DiscoverEvent

    data class OpenUrl(
        val url: String,
    ) : DiscoverEvent

    data class Share(
        val text: String,
    ) : DiscoverEvent

    data class ShowMessage(
        val message: String,
    ) : DiscoverEvent
}

/**
 * 商店发现 ViewModel：
 * - 观察 Room 发现列表并按分类筛选；
 * - 下拉刷新触发并发 Release 拉取；
 * - 点击卡片进入 Release 详情；下载按钮解析镜像链接后交 UI 用浏览器/系统打开。
 */
@Suppress("LongParameterList") // 7 个用例注入，属 ViewModel 聚合入口的常规形态
class DiscoverViewModel(
    private val observeDiscover: ObserveDiscoverUseCase,
    private val refreshDiscover: RefreshDiscoverUseCase,
    private val resolveLink: ResolveDownloadLinkUseCase,
    private val buildShareText: BuildShareTextUseCase,
    private val shareManager: ShareManager,
    private val enqueueDownload: EnqueueDownloadUseCase,
    private val searchRepos: SearchUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DiscoverUiState())
    val uiState: StateFlow<DiscoverUiState> = _uiState.asStateFlow()

    private val _events = Channel<DiscoverEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            observeDiscover().collect { all ->
                _uiState.value = _uiState.value.copy(allItems = all)
            }
        }
        refresh()
    }

    fun onCategorySelected(category: DiscoverCategory) {
        // 仅切换分类，派生属性会即时重算可见列表，无需重复订阅或请求网络。
        _uiState.value = _uiState.value.copy(category = category)
    }

    /** 切换榜单 tab；非精选时按排序拉取仓库榜（v0.10.0 N5 / v0.11.0 N6 趋势）。 */
    fun onRankTabSelected(tab: RankTab) {
        _uiState.value = _uiState.value.copy(rankTab = tab)
        if (tab == RankTab.FEATURED) return
        loadRank(tab, _uiState.value.rankLanguage)
    }

    /** v0.11.0 N6：切换语言筛选后重拉当前榜单。 */
    fun onRankLanguageSelected(language: String) {
        _uiState.value = _uiState.value.copy(rankLanguage = language)
        val tab = _uiState.value.rankTab
        if (tab != RankTab.FEATURED) loadRank(tab, language)
    }

    private fun loadRank(
        tab: RankTab,
        language: String,
    ) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRankLoading = true, rankNote = rankNoteFor(tab))
            val trending = tab == RankTab.TRENDING
            val query =
                SearchQuery(
                    term = RankQueryFactory.buildTerm(language, RankQueryFactory.sinceDate(), trending),
                    type = SearchType.REPOSITORY,
                    sort = if (tab == RankTab.NEW) "updated" else "stars",
                    order = SearchOrder.DESC,
                    perPage = RANK_PER_PAGE,
                )
            when (val result = searchRepos(query)) {
                is NetworkResult.Success ->
                    _uiState.value =
                        _uiState.value.copy(
                            rankItems = result.data.items.filterIsInstance<RepoHit>(),
                            isRankLoading = false,
                        )

                is NetworkResult.Failure ->
                    _uiState.value =
                        _uiState.value.copy(rankItems = emptyList(), isRankLoading = false)
            }
        }
    }

    /** 榜单口径明示：趋势为「近 30 天新建」近似，其余为 Search API 排序，均非官方 Trending。 */
    private fun rankNoteFor(tab: RankTab): String =
        when (tab) {
            RankTab.FEATURED -> ""
            RankTab.TRENDING -> "非官方近似 · 近 30 天新建按 ★ 排序 · 基于 Search API"
            RankTab.NEW -> "非官方榜单 · 按最近更新排序 · 基于 Search API"
            RankTab.HOT -> "非官方榜单 · 按 ★ 排序 · 基于 Search API"
        }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true)
            when (val result = refreshDiscover()) {
                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(isRefreshing = false)
                }

                is NetworkResult.Failure -> {
                    _uiState.value = _uiState.value.copy(isRefreshing = false)
                    _events.trySend(DiscoverEvent.ShowMessage(discoverErrorMessage(result.error)))
                }
            }
        }
    }

    fun onOpenDetail(item: DiscoverItem) {
        _uiState.value = _uiState.value.copy(selected = item)
        _events.trySend(DiscoverEvent.OpenRelease(item.featured.owner, item.featured.name))
    }

    fun onClearSelection() {
        _uiState.value = _uiState.value.copy(selected = null)
    }

    fun resolveDownload(assetUrl: String): MirrorDownloadLink = resolveLink(assetUrl)

    /** 提交 Release 资产下载任务到下载管理器（App 内下载，支持断点续传与镜像）。 */
    fun onDownloadAsset(
        asset: ReleaseAsset,
        owner: String,
        name: String,
        tag: String,
    ) {
        val link = resolveLink(asset.downloadUrl)
        val request = buildAssetDownloadRequest(asset, owner, name, tag, link)
        viewModelScope.launch { enqueueDownload(request) }
    }

    fun onShare(item: DiscoverItem) {
        val text = buildShareText(item)
        // 系统分享通过注入的 ShareManager 直接触发；事件用于 UI 侧同步提示。
        shareManager.shareText(text)
        _events.trySend(DiscoverEvent.Share(text))
    }

    private companion object {
        const val RANK_PER_PAGE = 20
    }
}
