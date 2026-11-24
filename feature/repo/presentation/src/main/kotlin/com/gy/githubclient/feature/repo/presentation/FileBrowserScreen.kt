/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.feature.repo.domain.model.FileContent
import com.gy.githubclient.feature.repo.domain.model.FileEntry
import com.gy.githubclient.feature.repo.domain.model.RepoTree
import com.gy.githubclient.feature.repo.domain.model.TreeNode
import org.koin.androidx.compose.koinViewModel

/**
 * 文件浏览器（v0.9.0 M18）：以默认分支为 ref，逐级浏览仓库文件树。
 * 点目录入栈、点文件读取纯文本；大文件/二进制引导浏览器打开 download_url。
 */
@Composable
fun FileBrowserScreen(
    owner: String,
    name: String,
    ref: String,
    onBack: () -> Unit = {},
    viewModel: FileBrowserViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(owner, name, ref) {
        viewModel.init(owner, name, ref)
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("← 返回") }
            Text("$owner/$name", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            // v0.16.0 P2-a：在逐级浏览 / 完整文件树之间切换。
            TextButton(onClick = { viewModel.toggleTreeMode() }) {
                Text(if (uiState.treeMode) "📁 逐级" else "🌳 完整树")
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { viewModel.navigateUp() }, enabled = uiState.currentPath.isNotBlank()) {
                Text("↑ 上级")
            }
            Text(
                uiState.currentPath.ifBlank { "/ (根目录)" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }

        uiState.error?.let { error ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { viewModel.dismissError() }) { Text("关闭") }
                }
            }
        }

        val openFile = uiState.openFile
        when {
            uiState.treeMode -> {
                TreeView(tree = uiState.tree, isLoading = uiState.isLoading)
            }

            openFile != null -> {
                FileContentView(
                    file = openFile,
                    fileHistory = uiState.fileHistory,
                    onShowHistory = { viewModel.showFileHistory() },
                    onClose = { viewModel.closeFile() },
                )
            }

            else -> {
                if (uiState.isLoading) {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (!uiState.isLoading && uiState.entries.isEmpty() && uiState.error == null) {
                        item {
                            Text(
                                "该目录为空。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    items(uiState.entries, key = { "entry_${it.path}" }) { entry ->
                        FileEntryRow(
                            entry = entry,
                            onClick = {
                                if (entry.isDirectory) viewModel.enterDirectory(entry.name) else viewModel.openFile(entry)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileEntryRow(
    entry: FileEntry,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (entry.isDirectory) "📁" else "📄")
        Text(
            entry.name,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
        )
        if (!entry.isDirectory) {
            Text(
                formatFileSize(entry.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 完整文件树视图（v0.16.0 P2-a）：一次展示递归树全部节点，含截断提示。 */
@Composable
private fun TreeView(
    tree: RepoTree?,
    isLoading: Boolean,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (isLoading && tree == null) {
            Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        if (tree != null) {
            Text(
                "文件 ${tree.fileCount} · 目录 ${tree.dirCount}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            if (tree.isTruncated) {
                Text(
                    "⚠ GitHub 返回的树已截断（文件过多），当前仅显示部分节点。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                items(tree.nodes, key = { "tree_${it.type}_${it.path}" }) { node ->
                    TreeNodeRow(node = node)
                }
            }
        } else if (!isLoading) {
            Text("尚未加载文件树。", style = MaterialTheme.typography.bodySmall)
        }
        Text(
            "数据来源：GitHub REST · git/trees?recursive=1（只读）",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun TreeNodeRow(node: TreeNode) {
    val depth = node.path.count { it == '/' }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp, horizontal = (depth * 8).dp.coerceAtMost(48.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(if (node.isDirectory) "📁" else "📄")
        Text(
            node.path.substringAfterLast('/'),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f).padding(start = 6.dp),
        )
        if (node.isFile) {
            Text(
                formatFileSize(node.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FileContentView(
    file: FileContent,
    fileHistory: List<com.gy.githubclient.feature.repo.domain.model.CommitSummary>?,
    onShowHistory: () -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose) { Text("← 返回目录") }
            Text(file.name, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            TextButton(onClick = onShowHistory) { Text("🕘 文件历史") }
        }
        // v0.14.0 A4 降级：REST 无公开 blame 端点，改用 commits?path= 展示逐次提交。
        if (fileHistory != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("文件提交历史（${fileHistory.size}）", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    if (fileHistory.isEmpty()) {
                        Text(
                            "该文件暂无提交历史记录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        fileHistory.take(FILE_HISTORY_LIMIT).forEach { commit ->
                            Text(
                                "${commit.shortSha}  ${commit.title}",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    Text(
                        "数据来源：GitHub REST · commits?path=（非逐行 blame）",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        if (file.isTextUnavailable) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text(
                        "该文件过大或非文本文件，无法在 App 内展示（${formatFileSize(file.size)}）。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    file.downloadUrl?.let { url ->
                        TextButton(onClick = { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }) {
                            Text("🌐 在浏览器中打开")
                        }
                    }
                }
            }
        } else {
            // v0.11.0 N8：零依赖轻量语法高亮；按扩展名给语言提示，token 着色后等宽展示。
            val languageHint = file.name.substringAfterLast('.', "")
            val annotated = rememberHighlightedCode(file.content, languageHint)
            Text(
                annotated,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val FILE_HISTORY_LIMIT = 20

/** 把词法 token 序列着色为 AnnotatedString（缓存到内容/语言不变为止）。 */
@Composable
private fun rememberHighlightedCode(
    content: String,
    languageHint: String,
): AnnotatedString =
    androidx.compose.runtime.remember(content, languageHint) {
        val tokens = tokenizeCode(content, languageHint)
        buildAnnotatedString {
            tokens.forEach { token ->
                val color =
                    when (token.kind) {
                        TokenKind.KEYWORD -> Color(0xFF7B5CD6)
                        TokenKind.STRING -> Color(0xFF2E8B57)
                        TokenKind.COMMENT -> Color(0xFF9E9E9E)
                        TokenKind.NUMBER -> Color(0xFFC67600)
                        TokenKind.FUNCTION -> Color(0xFF1A73E8)
                        TokenKind.PLAIN -> Color.Unspecified
                    }
                if (color == Color.Unspecified) append(token.text) else withStyle(SpanStyle(color = color)) { append(token.text) }
            }
        }
    }
