/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.download.domain.model.DownloadRequest
import com.gy.githubclient.feature.download.domain.usecase.EnqueueDownloadUseCase
import com.gy.githubclient.feature.repo.domain.model.Branch
import com.gy.githubclient.feature.repo.domain.model.IssueDetail
import com.gy.githubclient.feature.repo.domain.model.IssueItem
import com.gy.githubclient.feature.repo.domain.model.Readme
import com.gy.githubclient.feature.repo.domain.model.RepoFilter
import com.gy.githubclient.feature.repo.domain.model.RepoType
import com.gy.githubclient.feature.repo.domain.model.Repository
import com.gy.githubclient.feature.repo.domain.model.StarHistory
import com.gy.githubclient.feature.repo.domain.usecase.ForkRepositoryUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetIssueDetailUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetReadmeUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetRepositoryUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetStarHistoryUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListBranchesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListIssuesUseCase
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

/** README 加载状态。 */
sealed class ReadmeUiState {
    data object Idle : ReadmeUiState()

    data object Loading : ReadmeUiState()

    data object NotAvailable : ReadmeUiState()

    data class Loaded(
        val readme: Readme,
    ) : ReadmeUiState()
}

/**
 * 仓库列表 ViewModel：
 * - [repositories] 由 filter 驱动观察 Room 缓存，切换筛选条件自动换流。
 * - 刷新/加 star 走网络，失败以事件提示，缓存数据始终保持可见。
 */
@Suppress("LongParameterList") // 用例逐一显式注入（Koin 构造式 DI），超出阈值属预期
class RepoViewModel(
    private val observeRepositories: ObserveRepositoriesUseCase,
    private val refreshRepositories: RefreshRepositoriesUseCase,
    private val setStarred: SetStarredUseCase,
    private val forkRepository: ForkRepositoryUseCase,
    private val getRepository: GetRepositoryUseCase,
    private val listBranches: ListBranchesUseCase,
    private val getReadme: GetReadmeUseCase,
    private val getStarHistory: GetStarHistoryUseCase,
    private val listIssues: ListIssuesUseCase,
    private val getIssueDetail: GetIssueDetailUseCase,
    private val enqueueDownload: EnqueueDownloadUseCase,
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

    /** 详情页 README 状态（v0.6.0 M6）。 */
    private val _readme = MutableStateFlow<ReadmeUiState>(ReadmeUiState.Idle)
    val readme: StateFlow<ReadmeUiState> = _readme.asStateFlow()

    /** 详情页星标趋势（v0.7.0 M10）：null=加载中/失败，空列表=无历史。 */
    private val _starHistory = MutableStateFlow<StarHistory?>(null)
    val starHistory: StateFlow<StarHistory?> = _starHistory.asStateFlow()

    /** Issue / PR 状态（v0.7.0 M11）。 */
    private val _issues = MutableStateFlow<List<IssueItem>>(emptyList())
    val issues: StateFlow<List<IssueItem>> = _issues.asStateFlow()

    private val _issueDetail = MutableStateFlow<IssueDetail?>(null)
    val issueDetail: StateFlow<IssueDetail?> = _issueDetail.asStateFlow()

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
                    loadReadme(owner, name)
                    loadStarHistory(owner, name)
                    loadIssues(owner, name)
                }

                is NetworkResult.Failure -> _events.trySend(RepoEvent.ShowMessage(errorMessage(result.error)))
            }
        }
    }

    fun toggleStar(repo: Repository) {
        viewModelScope.launch {
            val next = !repo.isStarred
            when (val result = setStarred(repo.owner.login, repo.name, next)) {
                is NetworkResult.Success -> {
                    // 同步详情页状态，避免按钮文案停留在旧值。
                    if (_detail.value?.fullName == repo.fullName) {
                        _detail.value = _detail.value?.copy(isStarred = next)
                    }
                    _events.trySend(
                        RepoEvent.ShowMessage(if (next) "已 Star ${repo.fullName}" else "已取消 Star ${repo.fullName}"),
                    )
                }

                is NetworkResult.Failure -> _events.trySend(RepoEvent.ShowMessage(errorMessage(result.error)))
            }
        }
    }

    /**
     * 拉取 README（v0.6.0 M6）：失败静默显示占位文案，不打扰详情主流程。
     */
    private fun loadReadme(owner: String, name: String) {
        viewModelScope.launch {
            _readme.value = ReadmeUiState.Loading
            _readme.value =
                when (val result = getReadme(owner, name)) {
                    is NetworkResult.Success ->
                        result.data?.let(ReadmeUiState::Loaded) ?: ReadmeUiState.NotAvailable

                    is NetworkResult.Failure -> ReadmeUiState.NotAvailable
                }
        }
    }

    /** 当前详情仓库坐标，供 Issue 详情按需加载。 */
    private var currentOwner: String = ""
    private var currentRepo: String = ""

    /** 拉取 Issue / PR 列表（v0.7.0 M11），失败静默以空列表降级。 */
    private fun loadIssues(
        owner: String,
        name: String,
    ) {
        currentOwner = owner
        currentRepo = name
        viewModelScope.launch {
            _issueDetail.value = null
            _issues.value =
                when (val result = listIssues(owner, name)) {
                    is NetworkResult.Success -> result.data
                    is NetworkResult.Failure -> emptyList()
                }
        }
    }

    /** 展开某条 Issue / PR 的详情与评论时间线。 */
    fun openIssue(number: Int) {
        viewModelScope.launch {
            _issueDetail.value =
                when (val result = getIssueDetail(currentOwner, currentRepo, number)) {
                    is NetworkResult.Success -> result.data
                    is NetworkResult.Failure -> {
                        _events.trySend(RepoEvent.ShowMessage("Issue 详情加载失败：${errorMessage(result.error)}"))
                        null
                    }
                }
        }
    }

    /** 收起 Issue 详情。 */
    fun closeIssue() {
        _issueDetail.value = null
    }

    /**
     * 拉取星标趋势（v0.7.0 M10）：按周累计曲线。失败静默显示占位，不打扰详情主流程。
     */
    private fun loadStarHistory(
        owner: String,
        name: String,
    ) {
        viewModelScope.launch {
            _starHistory.value =
                when (val result = getStarHistory(owner, name)) {
                    is NetworkResult.Success -> result.data
                    is NetworkResult.Failure -> StarHistory(emptyList())
                }
        }
    }

    /** Fork 到当前用户账号（GitHub 202 异步处理，稍后在仓库列表可见）。 */
    fun fork(repo: Repository) {
        viewModelScope.launch {
            when (val result = forkRepository(repo.owner.login, repo.name)) {
                is NetworkResult.Success ->
                    _events.trySend(
                        RepoEvent.ShowMessage("已发起 Fork → ${result.data.fullName}，稍后可在你的仓库列表查看"),
                    )

                is NetworkResult.Failure ->
                    _events.trySend(RepoEvent.ShowMessage("Fork 失败：${errorMessage(result.error)}"))
            }
        }
    }

    /**
     * 快捷下载仓库 ZIP（默认分支）：
     * /archive/refs/heads/{branch}.zip 走既有下载引擎（断点续传/镜像/前台通知全部复用）。
     * archive 无官方 digest，expectedSha256 置空跳过完整性校验。
     */
    fun downloadZip(repo: Repository) {
        viewModelScope.launch {
            val branch = repo.defaultBranch.ifBlank { "main" }
            val fileName = "${repo.name}-$branch.zip"
            val request =
                DownloadRequest(
                    url = "https://github.com/${repo.owner.login}/${repo.name}/archive/refs/heads/$branch.zip",
                    fileName = fileName,
                    suggestedFileName = fileName,
                    repoOwner = repo.owner.login,
                    repoName = repo.name,
                    releaseTag = branch,
                    assetId = 0L,
                    totalBytes = -1L,
                    useMirror = true,
                )
            runCatching { enqueueDownload(request) }
                .onSuccess { _events.trySend(RepoEvent.ShowMessage("已加入下载队列：$fileName")) }
                .onFailure { _events.trySend(RepoEvent.ShowMessage("下载失败：${it.message ?: "未知错误"}")) }
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
