/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.network.ApiError
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 榜单类型：精选 / 趋势（v0.11.0 N6）/ 新发布 / 热门。 */
enum class RankTab {
    FEATURED,
    TRENDING,
    NEW,
    HOT,
}

/**
 * 榜单口径说明的分类。
 *
 * 只表达"哪种口径"，文案留在界面层（`RankNoteKind.asText()`）——
 * 原先 ViewModel 直接返回中文句子，属于「ViewModel 输出界面文案」的历史缺陷。
 */
enum class RankNoteKind {
    TRENDING,
    NEW,
    HOT,
}

/**
 * 刷新失败的原因分类。
 *
 * 同样只给分类不给文案；保留原始 `cause` / `code` 供界面层带参数展示，
 * 避免为了"不出现字符串"而把可用信息（如 HTTP 状态码）丢掉。
 */
sealed interface DiscoverError {
    data object Unauthorized : DiscoverError

    data object RateLimited : DiscoverError

    data object NotFound : DiscoverError

    data class Network(
        val cause: String,
    ) : DiscoverError

    data class Http(
        val code: Int,
    ) : DiscoverError

    data object Unknown : DiscoverError
}

/** 发现页 UI 状态。 */
data class DiscoverUiState(
    val allItems: List<DiscoverItem> = emptyList(),
    val category: DiscoverCategory = DiscoverCategory.ALL,
    val isRefreshing: Boolean = false,
    /** 刷新失败原因；null 表示无错误。 */
    val refreshError: DiscoverError? = null,
    val rankTab: RankTab = RankTab.FEATURED,
    val rankItems: List<RepoHit> = emptyList(),
    val isRankLoading: Boolean = false,
    /** v0.11.0 N6：语言筛选，取值为 RankQueryFactory.LANGUAGES 之一。 */
    val rankLanguage: String = RankQueryFactory.LANGUAGES.first(),
    /** 榜单查询口径（非官方近似标注），随 tab 变化；精选页为 null。 */
    val rankNote: RankNoteKind? = null,
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

/**
 * 商店发现 ViewModel：
 * - 观察 Room 发现列表并按分类筛选；
 * - 刷新触发并发 Release 拉取，失败原因以语义类型暴露给界面层；
 * - 榜单 tab / 语言切换时复用 Search API 拉仓库榜。
 */
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
            _uiState.value = _uiState.value.copy(isRankLoading = true, rankNote = tab.toRankNoteKind())
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

    private fun RankTab.toRankNoteKind(): RankNoteKind? =
        when (this) {
            RankTab.FEATURED -> null
            RankTab.TRENDING -> RankNoteKind.TRENDING
            RankTab.NEW -> RankNoteKind.NEW
            RankTab.HOT -> RankNoteKind.HOT
        }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isRefreshing = true, refreshError = null)
            when (val result = refreshDiscover()) {
                is NetworkResult.Success -> {
                    _uiState.value = _uiState.value.copy(isRefreshing = false)
                }

                is NetworkResult.Failure -> {
                    _uiState.value =
                        _uiState.value.copy(
                            isRefreshing = false,
                            refreshError = result.error.toDiscoverError(),
                        )
                }
            }
        }
    }

    /** 分享当前精选条目（系统分享面板由注入的 ShareManager 触发）。 */
    fun onShare(item: DiscoverItem) {
        shareManager.shareText(buildShareText(item))
    }

    private fun ApiError.toDiscoverError(): DiscoverError =
        when (this) {
            is ApiError.Unauthorized -> DiscoverError.Unauthorized
            is ApiError.RateLimited -> DiscoverError.RateLimited
            is ApiError.NotFound -> DiscoverError.NotFound
            is ApiError.Network -> DiscoverError.Network(cause)
            is ApiError.Http -> DiscoverError.Http(code)
            is ApiError.Unknown -> DiscoverError.Unknown
        }

    /**
     * 解析资产的镜像 / 直链。
     *
     * 注意：这两个方法**不是死代码** —— `ReleaseDetailScreen` 复用的就是本 ViewModel。
     * 它们职责上更应归属 ReleaseDetail 专属 VM（债务 D-03），拆分需连带改详情页，另开 PR。
     */
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

    private companion object {
        const val RANK_PER_PAGE = 20
    }
}
