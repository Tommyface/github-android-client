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
import com.gy.githubclient.feature.discover.domain.model.ReleaseAsset
import com.gy.githubclient.feature.discover.domain.usecase.BuildShareTextUseCase
import com.gy.githubclient.feature.discover.domain.usecase.ObserveDiscoverUseCase
import com.gy.githubclient.feature.discover.domain.usecase.RefreshDiscoverUseCase
import com.gy.githubclient.feature.discover.domain.usecase.ResolveDownloadLinkUseCase
import com.gy.githubclient.feature.download.domain.model.DownloadRequest
import com.gy.githubclient.feature.download.domain.usecase.EnqueueDownloadUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

/** 发现页 UI 状态。 */
data class DiscoverUiState(
    val allItems: List<DiscoverItem> = emptyList(),
    val category: DiscoverCategory = DiscoverCategory.ALL,
    val isRefreshing: Boolean = false,
    val selected: DiscoverItem? = null,
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
class DiscoverViewModel(
    private val observeDiscover: ObserveDiscoverUseCase,
    private val refreshDiscover: RefreshDiscoverUseCase,
    private val resolveLink: ResolveDownloadLinkUseCase,
    private val buildShareText: BuildShareTextUseCase,
    private val shareManager: ShareManager,
    private val enqueueDownload: EnqueueDownloadUseCase,
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
                    _events.trySend(DiscoverEvent.ShowMessage(errorMessage(result.error)))
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
        val request =
            DownloadRequest(
                url = asset.downloadUrl,
                fileName = asset.name.ifBlank { "download.bin" },
                suggestedFileName = asset.name.ifBlank { null },
                repoOwner = owner,
                repoName = name,
                releaseTag = tag,
                assetId = asset.id,
                totalBytes = asset.size,
                useMirror = link.isAccelerated,
                expectedSha256 = asset.sha256,
            )
        viewModelScope.launch { enqueueDownload(request) }
    }

    fun onShare(item: DiscoverItem) {
        val text = buildShareText(item)
        // 系统分享通过注入的 ShareManager 直接触发；事件用于 UI 侧同步提示。
        shareManager.shareText(text)
        _events.trySend(DiscoverEvent.Share(text))
    }

    private fun errorMessage(error: ApiError): String =
        when (error) {
            ApiError.Unauthorized -> "登录已失效，请重新登录"
            is ApiError.RateLimited -> "请求太频繁，已触发 API 限流，请稍后再试"
            ApiError.NotFound -> "未找到资源"
            is ApiError.Network -> "网络错误：${error.cause}"
            is ApiError.Http -> "请求失败（${error.code}）"
            is ApiError.Unknown -> "未知错误：${error.message}"
        }
}
