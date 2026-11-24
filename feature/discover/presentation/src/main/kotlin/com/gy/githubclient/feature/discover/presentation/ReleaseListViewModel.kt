/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.discover.domain.model.MirrorDownloadLink
import com.gy.githubclient.feature.discover.domain.model.ReleaseAsset
import com.gy.githubclient.feature.discover.domain.model.ReleaseInfo
import com.gy.githubclient.feature.discover.domain.model.TagInfo
import com.gy.githubclient.feature.discover.domain.usecase.ListReleasesUseCase
import com.gy.githubclient.feature.discover.domain.usecase.ListTagsUseCase
import com.gy.githubclient.feature.discover.domain.usecase.ResolveDownloadLinkUseCase
import com.gy.githubclient.feature.download.domain.usecase.EnqueueDownloadUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 多版本列表的展示页签。 */
enum class ReleaseTab {
    RELEASES,
    TAGS,
}

/** 多版本列表 UI 状态（v0.8.0 M15）。 */
data class ReleaseListUiState(
    val releases: List<ReleaseInfo> = emptyList(),
    val tags: List<TagInfo> = emptyList(),
    val tab: ReleaseTab = ReleaseTab.RELEASES,
    val includePrerelease: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
)

/**
 * 任意仓库的多版本 Release / Tag 列表 ViewModel。
 * 与 [DiscoverViewModel] 分离：本 VM 只负责 owner/name 指定的单个仓库，不触碰精选缓存。
 */
class ReleaseListViewModel(
    private val listReleases: ListReleasesUseCase,
    private val listTags: ListTagsUseCase,
    private val resolveLink: ResolveDownloadLinkUseCase,
    private val enqueueDownload: EnqueueDownloadUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ReleaseListUiState())
    val uiState: StateFlow<ReleaseListUiState> = _uiState.asStateFlow()

    private var owner: String = ""
    private var name: String = ""

    /** 由 Screen 的 LaunchedEffect(owner, name) 调用，加载指定仓库的发布与标签。 */
    fun load(
        owner: String,
        name: String,
    ) {
        this.owner = owner
        this.name = name
        refresh()
    }

    fun refresh() {
        if (owner.isBlank() || name.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val includePre = _uiState.value.includePrerelease

            when (val releases = listReleases(owner, name, includePrerelease = includePre)) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(releases = releases.data)
                is NetworkResult.Failure ->
                    _uiState.value =
                        _uiState.value.copy(error = discoverErrorMessage(releases.error))
            }

            // Tags 失败不阻塞 Releases 主流程，静默保留空列表。
            when (val tags = listTags(owner, name)) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(tags = tags.data)
                is NetworkResult.Failure -> Unit
            }

            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun selectTab(tab: ReleaseTab) {
        _uiState.value = _uiState.value.copy(tab = tab)
    }

    fun setIncludePrerelease(include: Boolean) {
        _uiState.value = _uiState.value.copy(includePrerelease = include)
        refresh()
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun resolveDownload(assetUrl: String): MirrorDownloadLink = resolveLink(assetUrl)

    /** 提交资产下载：复用发现页同一构造逻辑（镜像 + SHA-256 + 溯源字段）。 */
    fun onDownloadAsset(
        asset: ReleaseAsset,
        releaseTag: String,
    ) {
        val link = resolveLink(asset.downloadUrl)
        val request = buildAssetDownloadRequest(asset, owner, name, releaseTag, link)
        viewModelScope.launch { enqueueDownload(request) }
    }
}
