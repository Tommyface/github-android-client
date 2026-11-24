/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.domain.model.CommitSummary
import com.gy.githubclient.feature.repo.domain.model.FileContent
import com.gy.githubclient.feature.repo.domain.model.FileEntry
import com.gy.githubclient.feature.repo.domain.model.RepoTree
import com.gy.githubclient.feature.repo.domain.usecase.GetFileContentUseCase
import com.gy.githubclient.feature.repo.domain.usecase.GetTreeUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListContentsUseCase
import com.gy.githubclient.feature.repo.domain.usecase.ListFileCommitsUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** 文件浏览器 UI 状态（v0.9.0 M18）。 */
data class FileBrowserUiState(
    /** 当前目录路径（空串 = 仓库根目录）。 */
    val currentPath: String = "",
    val entries: List<FileEntry> = emptyList(),
    val openFile: FileContent? = null,
    val isLoading: Boolean = false,
    val error: String? = null,
    /** 当前打开文件的提交历史（v0.14.0 A4 降级）；null=未加载。 */
    val fileHistory: List<CommitSummary>? = null,
    /** 是否处于「完整文件树」视图（v0.16.0 P2-a）。 */
    val treeMode: Boolean = false,
    /** 递归文件树（v0.16.0 P2-a）；null=未加载。 */
    val tree: RepoTree? = null,
) {
    /** 面包屑：根目录 + 各段。 */
    val breadcrumbs: List<String>
        get() = if (currentPath.isBlank()) listOf("/") else listOf("/") + currentPath.split('/')
}

/**
 * 文件浏览器 ViewModel（v0.9.0 M17/M18）：逐级浏览 contents 端点，
 * 点目录入栈、点文件读取内容；与仓库详情状态解耦，独立生命周期。
 */
class FileBrowserViewModel(
    private val listContents: ListContentsUseCase,
    private val getFileContent: GetFileContentUseCase,
    private val listFileCommits: ListFileCommitsUseCase,
    private val getTree: GetTreeUseCase,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FileBrowserUiState())
    val uiState: StateFlow<FileBrowserUiState> = _uiState.asStateFlow()

    private var owner: String = ""
    private var name: String = ""
    private var ref: String = ""

    fun init(
        owner: String,
        name: String,
        ref: String,
    ) {
        this.owner = owner
        this.name = name
        this.ref = ref
        _uiState.value = FileBrowserUiState()
        loadDirectory("")
    }

    /** 切换「逐级目录 / 完整文件树」视图（v0.16.0 P2-a）。 */
    fun toggleTreeMode() {
        val next = !_uiState.value.treeMode
        _uiState.value = _uiState.value.copy(treeMode = next, error = null)
        if (next && _uiState.value.tree == null) loadTree()
    }

    /** 加载递归文件树（v0.16.0 P2-a）：一次拉全树，只读展示。 */
    private fun loadTree() {
        if (owner.isBlank() || name.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = getTree(owner, name, ref)) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(tree = result.data)
                is NetworkResult.Failure ->
                    _uiState.value = _uiState.value.copy(error = "文件树加载失败：${result.error}")
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun loadDirectory(path: String) {
        if (owner.isBlank() || name.isBlank()) return
        viewModelScope.launch {
            _uiState.value =
                _uiState.value.copy(currentPath = path, isLoading = true, error = null, openFile = null, fileHistory = null)
            when (val result = listContents(owner, name, path, ref)) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(entries = result.data)
                is NetworkResult.Failure ->
                    _uiState.value =
                        _uiState.value.copy(entries = emptyList(), error = "目录加载失败：${result.error}")
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /** 进入子目录：[currentPath] 为空时直接用 [name]，否则拼接。 */
    fun enterDirectory(name: String) {
        loadDirectory(childPath(_uiState.value.currentPath, name))
    }

    /** 返回上级目录；已在根目录时无操作。 */
    fun navigateUp() {
        val current = _uiState.value.currentPath
        if (current.isBlank()) return
        loadDirectory(parentPath(current))
    }

    fun openFile(entry: FileEntry) {
        if (owner.isBlank() || name.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null, fileHistory = null)
            when (val result = getFileContent(owner, name, entry.path, ref)) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(openFile = result.data)
                is NetworkResult.Failure ->
                    _uiState.value = _uiState.value.copy(error = "文件读取失败：${result.error}")
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    /**
     * 加载当前打开文件的提交历史（v0.14.0 A4 降级）：
     * GitHub REST 无公开 blame 端点（前置实测确认），改用 commits?path= 展示逐次提交，明示口径。
     */
    fun showFileHistory() {
        val file = _uiState.value.openFile ?: return
        if (owner.isBlank() || name.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            when (val result = listFileCommits(owner, name, file.path, ref)) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(fileHistory = result.data)
                is NetworkResult.Failure ->
                    _uiState.value = _uiState.value.copy(error = "文件历史加载失败：${result.error}")
            }
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }

    fun closeFile() {
        _uiState.value = _uiState.value.copy(openFile = null, fileHistory = null)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun refresh() {
        loadDirectory(_uiState.value.currentPath)
    }
}

/** 文件大小格式化（v0.9.0 M18）：纯函数，便于单测。 */
internal fun formatFileSize(bytes: Long): String =
    when {
        bytes <= 0 -> "—"
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        else -> "${bytes / (1024 * 1024)} MB"
    }

/** 目录入栈：根目录直接返回子名，否则拼接。 */
internal fun childPath(
    current: String,
    name: String,
): String = if (current.isBlank()) name else "$current/$name"

/** 目录出栈；根目录返回空串。 */
internal fun parentPath(current: String): String =
    if (current.isBlank()) "" else current.substringBeforeLast('/', "")
