/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.core.mutation.MutationResult
import com.gy.githubclient.core.mutation.userMessage
import com.gy.githubclient.feature.download.domain.model.DownloadRequest
import com.gy.githubclient.feature.download.domain.usecase.EnqueueDownloadUseCase
import com.gy.githubclient.feature.repo.domain.model.Branch
import com.gy.githubclient.feature.repo.domain.model.CheckOverview
import com.gy.githubclient.feature.repo.domain.model.Contributor
import com.gy.githubclient.feature.repo.domain.model.ForkItem
import com.gy.githubclient.feature.repo.domain.model.IssueDetail
import com.gy.githubclient.feature.repo.domain.model.IssueItem
import com.gy.githubclient.feature.repo.domain.model.LanguageShare
import com.gy.githubclient.feature.repo.domain.model.Milestone
import com.gy.githubclient.feature.repo.domain.model.Readme
import com.gy.githubclient.feature.repo.domain.model.RepoFilter
import com.gy.githubclient.feature.repo.domain.model.RepoLabel
import com.gy.githubclient.feature.repo.domain.model.RepoType
import com.gy.githubclient.feature.repo.domain.model.Repository
import com.gy.githubclient.feature.repo.domain.model.StarHistory
import com.gy.githubclient.feature.repo.domain.translation.TranslateTextUseCase
import com.gy.githubclient.feature.repo.domain.translation.TranslationEligibility
import com.gy.githubclient.feature.repo.domain.usecase.ForkRepositoryUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetCheckOverviewUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetIssueDetailUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetLanguagesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetReadmeUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetRepositoryUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetStarHistoryUseCase
import com.gy.githubclient.feature.repo.domain.usecase.IsWatchedUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListBranchesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListContributorsUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListForksUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListIssuesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListLabelsUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListMilestonesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ObserveRepositoriesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.RefreshRepositoriesUseCase
import com.gy.githubclient.feature.repo.domain.usecase.SetStarredUseCase
import com.gy.githubclient.feature.repo.domain.usecase.SetWatchedUseCase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
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
 * README 翻译状态（v0.16.0）：
 * - [Idle] 显示原文；[Loading] 正在逐段翻译；[Ready] 显示译文（[failed] 段失败时保留原文兜底）。
 */
sealed interface ReadmeTranslationUiState {
    data object Idle : ReadmeTranslationUiState

    data class Loading(
        val done: Int,
        val total: Int,
    ) : ReadmeTranslationUiState

    data class Ready(
        val failed: Int,
    ) : ReadmeTranslationUiState
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
    private val setIsWatched: SetWatchedUseCase,
    private val getIsWatched: IsWatchedUseCase,
    private val forkRepository: ForkRepositoryUseCase,
    private val getRepository: GetRepositoryUseCase,
    private val listBranches: ListBranchesUseCase,
    private val getReadme: GetReadmeUseCase,
    private val getStarHistory: GetStarHistoryUseCase,
    private val listIssues: ListIssuesUseCase,
    private val getIssueDetail: GetIssueDetailUseCase,
    private val getCheckOverview: GetCheckOverviewUseCase,
    private val getLanguages: GetLanguagesUseCase,
    private val listContributors: ListContributorsUseCase,
    private val listLabels: ListLabelsUseCase,
    private val listMilestones: ListMilestonesUseCase,
    private val listForks: ListForksUseCase,
    private val enqueueDownload: EnqueueDownloadUseCase,
    private val translateText: TranslateTextUseCase,
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

    /**
     * 当前选中的 ref（v0.13.0 A1）：默认分支，点击分支列表可切换。
     * 驱动「浏览文件」与「下载 ZIP」的 ref。
     */
    private val _selectedRef = MutableStateFlow("")
    val selectedRef: StateFlow<String> = _selectedRef.asStateFlow()

    /** 详情页 CI 检查概览（v0.13.0 A2）：null=加载中/无数据。 */
    private val _checkOverview = MutableStateFlow<CheckOverview?>(null)
    val checkOverview: StateFlow<CheckOverview?> = _checkOverview.asStateFlow()

    /** 详情页 README 状态（v0.6.0 M6）。 */
    private val _readme = MutableStateFlow<ReadmeUiState>(ReadmeUiState.Idle)
    val readme: StateFlow<ReadmeUiState> = _readme.asStateFlow()

    /** README 翻译状态（v0.16.0）：Idle=原文，Loading=逐段翻译，Ready=译文。 */
    private val _readmeTranslation = MutableStateFlow<ReadmeTranslationUiState>(ReadmeTranslationUiState.Idle)
    val readmeTranslation: StateFlow<ReadmeTranslationUiState> = _readmeTranslation.asStateFlow()

    /** 已译块：key=原文，value=译文；按块缓存，切换原文/译文无额外请求。 */
    private val _translatedBlocks = MutableStateFlow<Map<String, String>>(emptyMap())
    val translatedBlocks: StateFlow<Map<String, String>> = _translatedBlocks.asStateFlow()

    private var translationJob: Job? = null
    private var translationFailedCount = 0

    /** 详情页星标趋势（v0.7.0 M10）：null=加载中/失败，空列表=无历史。 */
    private val _starHistory = MutableStateFlow<StarHistory?>(null)
    val starHistory: StateFlow<StarHistory?> = _starHistory.asStateFlow()

    /** 当前用户是否 watch 该仓库；null=加载中/未知。 */
    private val _isWatched = MutableStateFlow<Boolean?>(null)
    val isWatched: StateFlow<Boolean?> = _isWatched.asStateFlow()

    /** Issue / PR 状态（v0.7.0 M11）。 */
    private val _issues = MutableStateFlow<List<IssueItem>>(emptyList())
    val issues: StateFlow<List<IssueItem>> = _issues.asStateFlow()

    private val _issueDetail = MutableStateFlow<IssueDetail?>(null)
    val issueDetail: StateFlow<IssueDetail?> = _issueDetail.asStateFlow()

    /** 语言构成（v0.16.0 P2-b）：按字节占比降序。 */
    private val _languages = MutableStateFlow<List<LanguageShare>>(emptyList())
    val languages: StateFlow<List<LanguageShare>> = _languages.asStateFlow()

    /** 贡献者（v0.16.0 P2-c）：只读列表。 */
    private val _contributors = MutableStateFlow<List<Contributor>>(emptyList())
    val contributors: StateFlow<List<Contributor>> = _contributors.asStateFlow()

    /** 仓库标签（v0.17.0 P3-a）：只读列表。 */
    private val _labels = MutableStateFlow<List<RepoLabel>>(emptyList())
    val labels: StateFlow<List<RepoLabel>> = _labels.asStateFlow()

    /** 里程碑（v0.17.0 P3-b）：只读列表。 */
    private val _milestones = MutableStateFlow<List<Milestone>>(emptyList())
    val milestones: StateFlow<List<Milestone>> = _milestones.asStateFlow()

    /** Fork 列表（v0.17.0 P3-c）：只读列表。 */
    private val _forks = MutableStateFlow<List<ForkItem>>(emptyList())
    val forks: StateFlow<List<ForkItem>> = _forks.asStateFlow()

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
                    // 默认分支作为初始选中 ref（A1）；空分支名兜底 main。
                    _selectedRef.value = result.data.defaultBranch.ifBlank { "main" }
                    resetReadmeTranslation()
                    _events.trySend(RepoEvent.OpenDetail)
                    loadBranches(owner, name)
                    loadWatched(owner, name)
                    loadReadme(owner, name)
                    loadStarHistory(owner, name)
                    loadIssues(owner, name)
                    loadChecks(owner, name, _selectedRef.value)
                    loadLanguages(owner, name)
                    loadContributors(owner, name)
                    loadLabels(owner, name)
                    loadMilestones(owner, name)
                    loadForks(owner, name)
                }

                is NetworkResult.Failure -> _events.trySend(RepoEvent.ShowMessage(errorMessage(result.error)))
            }
        }
    }

    fun toggleStar(repo: Repository) {
        viewModelScope.launch {
            val next = !repo.isStarred
            when (val result = setStarred(repo.owner.login, repo.name, next)) {
                is MutationResult.Success -> {
                    // 同步详情页状态，避免按钮文案停留在旧值。
                    if (_detail.value?.fullName == repo.fullName) {
                        _detail.value = _detail.value?.copy(isStarred = next)
                    }
                    _events.trySend(
                        RepoEvent.ShowMessage(if (next) "已 Star ${repo.fullName}" else "已取消 Star ${repo.fullName}"),
                    )
                }

                is MutationResult.Failure -> _events.trySend(RepoEvent.ShowMessage(result.error.userMessage()))
            }
        }
    }

    /** watch / unwatch：写成功后才更新按钮，失败保留原状态。 */
    fun toggleWatch(repo: Repository) {
        val current = _isWatched.value ?: return
        viewModelScope.launch {
            val next = !current
            when (val result = setIsWatched(repo.owner.login, repo.name, next)) {
                is MutationResult.Success -> {
                    _isWatched.value = next
                    _events.trySend(
                        RepoEvent.ShowMessage(if (next) "已 watch ${repo.fullName}" else "已取消 watch ${repo.fullName}"),
                    )
                }

                is MutationResult.Failure -> _events.trySend(RepoEvent.ShowMessage(result.error.userMessage()))
            }
        }
    }

    /** 订阅状态失败静默：不影响详情主流程，按钮保持不可用。 */
    private fun loadWatched(
        owner: String,
        name: String,
    ) {
        viewModelScope.launch {
            _isWatched.value =
                when (val result = getIsWatched(owner, name)) {
                    is NetworkResult.Success -> result.data
                    is NetworkResult.Failure -> null
                }
        }
    }

    /** 切换当前 ref（v0.13.0 A1）：分支名作为后续浏览文件 / 下载 ZIP / CI 的 ref。 */
    fun selectBranch(branchName: String) {
        if (branchName.isBlank() || branchName == _selectedRef.value) return
        _selectedRef.value = branchName
        val repo = _detail.value ?: return
        loadChecks(repo.owner.login, repo.name, branchName)
        _events.trySend(RepoEvent.ShowMessage("已切换到分支：$branchName"))
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

    /**
     * 切换 README 原文/译文（v0.16.0）：
     * - 第一次点击触发逐段翻译；已有译文时只做本地开关，零网络请求；
     * - 翻译中重复点击不打断当前任务。
     */
    fun toggleReadmeTranslation() {
        when (val state = _readmeTranslation.value) {
            is ReadmeTranslationUiState.Loading -> return
            is ReadmeTranslationUiState.Ready -> _readmeTranslation.value = ReadmeTranslationUiState.Idle
            ReadmeTranslationUiState.Idle -> {
                if (_translatedBlocks.value.isNotEmpty()) {
                    _readmeTranslation.value = ReadmeTranslationUiState.Ready(translationFailedCount)
                } else {
                    translateReadme()
                }
            }
        }
    }

    /**
     * 逐段翻译 README 正文（v0.16.0）：
     * 只翻标题/段落/列表项，代码块与图片原样保留；单段失败不阻塞后续，最终汇总失败段数。
     */
    private fun translateReadme() {
        val loaded =
            _readme.value as? ReadmeUiState.Loaded
                ?: run {
                    _events.trySend(RepoEvent.ShowMessage("README 尚未加载完成"))
                    return
                }
        val alreadyTranslated = _translatedBlocks.value
        val pending =
            collectTranslatableTexts(loaded.readme.content)
                .filterNot(alreadyTranslated::containsKey)
        if (pending.isEmpty() && alreadyTranslated.isEmpty()) {
            _events.trySend(RepoEvent.ShowMessage("README 没有可翻译的正文内容"))
            return
        }

        translationFailedCount = 0
        translationJob =
            viewModelScope.launch {
                val translated = alreadyTranslated.toMutableMap()
                var done = alreadyTranslated.size
                val total = done + pending.size
                _readmeTranslation.value = ReadmeTranslationUiState.Loading(done, total)
                for (text in pending) {
                    when (val result = translateText(text)) {
                        is NetworkResult.Success -> translated[text] = result.data
                        is NetworkResult.Failure -> translationFailedCount++
                    }
                    done++
                    _translatedBlocks.value = translated.toMap()
                    _readmeTranslation.value = ReadmeTranslationUiState.Loading(done, total)
                }
                _readmeTranslation.value = ReadmeTranslationUiState.Ready(translationFailedCount)
                if (translationFailedCount > 0) {
                    _events.trySend(
                        RepoEvent.ShowMessage("有 $translationFailedCount 段翻译失败，其余段落已翻译"),
                    )
                }
            }
    }

    /** 从 README 块中挑出值得翻译的文本（去重，保持出现顺序）。 */
    private fun collectTranslatableTexts(markdown: String): List<String> =
        parseMarkdownBlocks(markdown)
            .mapNotNull { block ->
                when (block) {
                    is MdBlock.Heading -> block.text.takeIf { TranslationEligibility.shouldTranslate(it, isHeading = true) }
                    is MdBlock.Paragraph -> block.text.takeIf { TranslationEligibility.shouldTranslate(it) }
                    is MdBlock.ListItem -> block.text.takeIf { TranslationEligibility.shouldTranslate(it) }
                    is MdBlock.CodeBlock, is MdBlock.Image, MdBlock.Rule -> null
                }
            }.distinct()

    /** 切换仓库时清空翻译状态，避免上一个仓库的译文串到新页面。 */
    private fun resetReadmeTranslation() {
        translationJob?.cancel()
        translationJob = null
        translationFailedCount = 0
        _translatedBlocks.value = emptyMap()
        _readmeTranslation.value = ReadmeTranslationUiState.Idle
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
     * 快捷下载仓库 ZIP（v0.13.0 A1：当前选中分支）：
     * /archive/refs/heads/{branch}.zip 走既有下载引擎（断点续传/镜像/前台通知全部复用）。
     * archive 无官方 digest，expectedSha256 置空跳过完整性校验。
     */
    fun downloadZip(repo: Repository) {
        viewModelScope.launch {
            val branch = _selectedRef.value.ifBlank { repo.defaultBranch.ifBlank { "main" } }
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

    /** 拉取 CI 检查概览（v0.13.0 A2）：ref 可传分支名；失败以空概览降级。 */
    private fun loadChecks(
        owner: String,
        name: String,
        ref: String,
    ) {
        viewModelScope.launch {
            _checkOverview.value =
                when (val result = getCheckOverview(owner, name, ref)) {
                    is NetworkResult.Success -> result.data
                    is NetworkResult.Failure -> CheckOverview(sha = ref)
                }
        }
    }

    /** 语言构成（v0.16.0 P2-b）：失败以空列表降级，不打扰详情主流程。 */
    private fun loadLanguages(
        owner: String,
        name: String,
    ) {
        viewModelScope.launch {
            _languages.value =
                when (val result = getLanguages(owner, name)) {
                    is NetworkResult.Success -> result.data
                    is NetworkResult.Failure -> emptyList()
                }
        }
    }

    /** 贡献者（v0.16.0 P2-c）：失败以空列表降级。 */
    private fun loadContributors(
        owner: String,
        name: String,
    ) {
        viewModelScope.launch {
            _contributors.value =
                when (val result = listContributors(owner, name)) {
                    is NetworkResult.Success -> result.data
                    is NetworkResult.Failure -> emptyList()
                }
        }
    }

    /** 仓库标签（v0.17.0 P3-a）：失败以空列表降级。 */
    private fun loadLabels(
        owner: String,
        name: String,
    ) {
        viewModelScope.launch {
            _labels.value =
                when (val result = listLabels(owner, name)) {
                    is NetworkResult.Success -> result.data
                    is NetworkResult.Failure -> emptyList()
                }
        }
    }

    /** 里程碑（v0.17.0 P3-b）：失败以空列表降级。 */
    private fun loadMilestones(
        owner: String,
        name: String,
    ) {
        viewModelScope.launch {
            _milestones.value =
                when (val result = listMilestones(owner, name)) {
                    is NetworkResult.Success -> result.data
                    is NetworkResult.Failure -> emptyList()
                }
        }
    }

    /** Fork 列表（v0.17.0 P3-c）：失败以空列表降级。 */
    private fun loadForks(
        owner: String,
        name: String,
    ) {
        viewModelScope.launch {
            _forks.value =
                when (val result = listForks(owner, name)) {
                    is NetworkResult.Success -> result.data
                    is NetworkResult.Failure -> emptyList()
                }
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
