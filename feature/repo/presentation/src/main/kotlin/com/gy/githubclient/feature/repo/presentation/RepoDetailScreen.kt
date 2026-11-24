/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.gy.githubclient.feature.repo.domain.model.IssueDetail
import com.gy.githubclient.feature.repo.domain.model.IssueItem
import com.gy.githubclient.feature.repo.domain.model.StarHistory
import com.gy.githubclient.feature.repo.domain.model.StarHistoryPoint
import org.koin.androidx.compose.koinViewModel

/** 仓库详情页：信息概览 + README（v0.6.0 M6）+ Star/Fork/ZIP 操作 + 分支列表。 */
@Composable
@Suppress("LongParameterList")
fun RepoDetailScreen(
    owner: String,
    name: String,
    onBack: () -> Unit,
    onOpenReleases: () -> Unit = {},
    onOpenFiles: (String) -> Unit = {},
    onOpenCommits: () -> Unit = {},
    viewModel: RepoViewModel = koinViewModel(),
) {
    val repo by viewModel.detail.collectAsStateWithLifecycle()
    val branches by viewModel.branches.collectAsStateWithLifecycle()
    val readmeState by viewModel.readme.collectAsStateWithLifecycle()
    val starHistory by viewModel.starHistory.collectAsStateWithLifecycle()
    val issues by viewModel.issues.collectAsStateWithLifecycle()
    val issueDetail by viewModel.issueDetail.collectAsStateWithLifecycle()

    LaunchedEffect(owner, name) {
        viewModel.openDetail(owner, name)
    }

    // 单一 LazyColumn：任意高度内容（README/分支）均完整渲染，不发生 Column 嵌套截断（v0.5.1 教训）。
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Button(onClick = onBack) { Text("返回") }
        }

        if (repo == null) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(top = 32.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            return@LazyColumn
        }

        val current = repo ?: return@LazyColumn

        item {
            Column {
                Text(current.fullName, style = MaterialTheme.typography.headlineSmall)
                current.description?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text("★ ${current.stargazersCount}", style = MaterialTheme.typography.labelLarge)
                    Text("⑂ ${current.forksCount}", style = MaterialTheme.typography.labelLarge)
                    Text("Issues ${current.openIssuesCount}", style = MaterialTheme.typography.labelLarge)
                }
                Spacer(Modifier.height(4.dp))
                Text("默认分支：${current.defaultBranch}", style = MaterialTheme.typography.bodySmall)
                current.language?.let { Text("语言：$it", style = MaterialTheme.typography.bodySmall) }
            }
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { viewModel.toggleStar(current) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (current.isStarred) "取消 Star" else "Star 这个仓库")
                }
                Button(onClick = { viewModel.fork(current) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (current.isFork) "这是一个 Fork" else "Fork 到我的账号")
                }
                OutlinedButton(onClick = { viewModel.downloadZip(current) }, modifier = Modifier.fillMaxWidth()) {
                    Text("⬇ 下载 ZIP（${current.defaultBranch}）")
                }
                OutlinedButton(onClick = onOpenReleases, modifier = Modifier.fillMaxWidth()) {
                    Text("📦 Releases / Tags")
                }
                OutlinedButton(onClick = { onOpenFiles(current.defaultBranch) }, modifier = Modifier.fillMaxWidth()) {
                    Text("📂 浏览文件")
                }
                OutlinedButton(onClick = onOpenCommits, modifier = Modifier.fillMaxWidth()) {
                    Text("🕘 提交历史 / Diff")
                }
            }
        }

        item {
            StarTrendCard(starHistory = starHistory)
        }

        item {
            IssueSection(
                issues = issues,
                detail = issueDetail,
                onOpenIssue = { viewModel.openIssue(it) },
                onCloseIssue = { viewModel.closeIssue() },
            )
        }

        item {
            Text("README", style = MaterialTheme.typography.titleMedium)
        }

        when (readmeState) {
            ReadmeUiState.Loading, ReadmeUiState.Idle -> {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            ReadmeUiState.NotAvailable -> {
                item {
                    Text(
                        "该仓库未提供 README",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            is ReadmeUiState.Loaded -> {
                val loaded = (readmeState as ReadmeUiState.Loaded).readme
                items(
                    parseMarkdownBlocks(loaded.content),
                    key = { it.renderKey() },
                    contentType = { it::class.simpleName },
                ) { block ->
                    MarkdownBlockView(
                        block = block,
                        owner = owner,
                        repo = name,
                        defaultBranch = current.defaultBranch,
                    )
                }
            }
        }

        item {
            Text("分支（${branches.size}）", style = MaterialTheme.typography.titleMedium)
        }
        items(branches, key = { "branch_${it.name}" }) { branch ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp)) {
                    Text(branch.name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (branch.isProtected) {
                        Text("受保护", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

/**
 * Issue / PR 只读区（v0.7.0 M11）：列表 + 点击展开详情/评论时间线。
 * 全部只读，不提供评论/关闭等写操作；正文复用 [renderInline] 轻量渲染。
 */
@Composable
internal fun IssueSection(
    issues: List<IssueItem>,
    detail: IssueDetail?,
    onOpenIssue: (Int) -> Unit,
    onCloseIssue: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Issues / PR（${issues.size}）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (issues.isEmpty()) {
                Text(
                    "暂无 Issue / PR",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                issues.take(ISSUE_PREVIEW_LIMIT).forEach { issue ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clickable { onOpenIssue(issue.number) }
                                .padding(vertical = 6.dp),
                    ) {
                        Text(
                            if (issue.isPullRequest) "⑂" else "◉",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.padding(start = 6.dp))
                        Text(
                            "#${issue.number} ${issue.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(issue.state, style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            if (detail != null) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                IssueDetailView(detail = detail, onClose = onCloseIssue)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "数据来源：GitHub REST · issues / comments（只读）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IssueDetailView(
    detail: IssueDetail,
    onClose: () -> Unit,
) {
    Column {
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                "#${detail.number} ${detail.title}",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onClose) { Text("收起") }
        }
        Text(
            "${if (detail.isPullRequest) "PR" else "Issue"} · ${detail.state} · by ${detail.author}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        detail.body?.let { body ->
            if (body.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(remember(body) { renderInline(body.take(ISSUE_BODY_PREVIEW_CHARS)) }, style = MaterialTheme.typography.bodySmall)
            }
        }
        if (detail.comments.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("评论（${detail.comments.size}）", style = MaterialTheme.typography.labelLarge)
            detail.comments.forEach { comment ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                ) {
                    Column(Modifier.padding(8.dp)) {
                        Text("${comment.author} · ${comment.createdAt.orEmpty()}", style = MaterialTheme.typography.labelSmall)
                        Spacer(Modifier.height(2.dp))
                        Text(
                            remember(comment.body) { renderInline(comment.body.take(ISSUE_BODY_PREVIEW_CHARS)) },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

private const val ISSUE_PREVIEW_LIMIT = 20
private const val ISSUE_BODY_PREVIEW_CHARS = 2000

/**
 * 星标趋势卡片（v0.7.0 M10）：
 * - 数据来自 GitHub stargazers/history（按周增量，客户端累加成累计曲线）；
 * - null=加载中；空列表=无历史；单点/全零也能稳定绘制；
 * - 页脚标注来源，遵循「每条数据可溯源」纪律。
 */
@Composable
internal fun StarTrendCard(starHistory: StarHistory?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("星标趋势（近 ${STAR_TREND_WEEKS} 周）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            when {
                starHistory == null ->
                    Text("加载中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                starHistory.points.isEmpty() ->
                    Text("该仓库暂无星标历史数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                else -> {
                    val last = starHistory.currentTotal
                    Text("当前 ★ $last（区间累计）", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    StarTrendCanvas(points = starHistory.points)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "数据来源：GitHub REST · stargazers/history（按周增量累加）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 用 Compose Canvas 自绘累计折线，零第三方图表依赖。 */
@Composable
private fun StarTrendCanvas(points: List<StarHistoryPoint>) {
    val lineColor = MaterialTheme.colorScheme.primary
    val baselineColor = MaterialTheme.colorScheme.outlineVariant
    Canvas(modifier = Modifier.fillMaxWidth().height(96.dp)) {
        val projected = projectTrend(points, canvasHeightPx = size.height - 8f, canvasWidthPx = size.width)
        if (projected.size < 2) return@Canvas
        val path =
            Path().apply {
                moveTo(projected.first().first, projected.first().second)
                projected.drop(1).forEach { lineTo(it.first, it.second) }
            }
        drawPath(path = path, color = lineColor, style = Stroke(width = 3f))
        projected.forEach { (x, y) ->
            drawCircle(color = lineColor, radius = 3f, center = Offset(x, y))
        }
        // 轴上基线，避免全零曲线贴边不可见。
        drawLine(
            color = baselineColor,
            start = Offset(0f, size.height - 4f),
            end = Offset(size.width, size.height - 4f),
            strokeWidth = 1f,
        )
    }
}

/**
 * 把累计序列投影到 [0,width]×[0,height] 的归一化坐标（纯函数，可单测）。
 * 纵坐标按最大值缩放；全零时统一落在底部，不会出现除零。
 * 横坐标按索引等距分布；单点时置于中线。
 */
internal fun projectTrend(
    points: List<StarHistoryPoint>,
    canvasHeightPx: Float,
    canvasWidthPx: Float = 1000f,
): List<Pair<Float, Float>> {
    if (points.isEmpty()) return emptyList()
    val maxY = points.maxOf { it.cumulativeStars }.coerceAtLeast(0)
    if (points.size == 1) return listOf(canvasWidthPx / 2f to canvasHeightPx)
    return points.mapIndexed { index, point ->
        val x = (index.toFloat() / (points.size - 1)) * canvasWidthPx
        val y = if (maxY == 0) canvasHeightPx else canvasHeightPx - (point.cumulativeStars.toFloat() / maxY) * canvasHeightPx
        x to y
    }
}

private const val STAR_TREND_WEEKS = 30

/** 块序列 key：同一 README 块列表稳定去重，避免 LazyColumn key 冲突。 */
private fun MdBlock.renderKey(): String =
    when (this) {
        is MdBlock.Heading -> "h$level-$text"
        is MdBlock.Paragraph -> "p-$text"
        is MdBlock.ListItem -> "li-$text"
        is MdBlock.CodeBlock -> "code-$code"
        is MdBlock.Image -> "img-$url"
        MdBlock.Rule -> "rule"
    }

/** 单块渲染：按块类型分派（README 卡片区域内，不再嵌套滚动）。 */
@Composable
private fun MarkdownBlockView(
    block: MdBlock,
    owner: String,
    repo: String,
    defaultBranch: String,
) {
    when (block) {
        is MdBlock.Heading ->
            Text(
                block.text,
                style =
                    when (block.level) {
                        1 -> MaterialTheme.typography.headlineSmall
                        2 -> MaterialTheme.typography.titleLarge
                        3 -> MaterialTheme.typography.titleMedium
                        else -> MaterialTheme.typography.titleSmall
                    },
            )

        is MdBlock.Paragraph ->
            Text(
                remember(block.text) { renderInline(block.text) },
                style = MaterialTheme.typography.bodyMedium,
            )

        is MdBlock.ListItem ->
            Row(Modifier.padding(start = 8.dp)) {
                Text("• ", style = MaterialTheme.typography.bodyMedium)
                Text(
                    remember(block.text) { renderInline(block.text) },
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

        is MdBlock.CodeBlock ->
            Card(Modifier.fillMaxWidth()) {
                Text(
                    block.code,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(12.dp),
                )
            }

        is MdBlock.Image -> {
            val url =
                remember(block.url, owner, repo, defaultBranch) {
                    rewriteReadmeImageUrl(block.url, owner, repo, defaultBranch)
                }
            Card(Modifier.fillMaxWidth()) {
                AsyncImage(
                    model = url,
                    contentDescription = block.alt,
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                )
            }
        }

        MdBlock.Rule ->
            androidx.compose.material3.HorizontalDivider()
    }
}

/**
 * 行内格式渲染（v0.6.0 M6）：`code` 等宽、**粗体**、[链接](url) 可点开。
 * 纯函数，可单测（返回 AnnotatedString 断言文本即可）。
 */
internal fun renderInline(text: String): AnnotatedString =
    buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            val codeStart = text.indexOf('`', index)
            val linkMatch = LINK_REGEX.matchAt(text, index)
            when {
                linkMatch != null -> {
                    val (label, url) = linkMatch.destructured
                    append(label)
                    append(" ($url)")
                    index = linkMatch.range.last + 1
                }

                codeStart >= 0 -> {
                    val codeEnd = text.indexOf('`', codeStart + 1)
                    if (codeEnd > codeStart) {
                        append(text, index, codeStart)
                        pushStyle(SPAN_CODE)
                        append(text.substring(codeStart + 1, codeEnd))
                        pop()
                        index = codeEnd + 1
                    } else {
                        append(text, index, text.length)
                        index = text.length
                    }
                }

                else -> {
                    val boldStart = text.indexOf("**", index)
                    if (boldStart >= 0) {
                        val boldEnd = text.indexOf("**", boldStart + 2)
                        if (boldEnd > boldStart) {
                            append(text, index, boldStart)
                            pushStyle(SPAN_BOLD)
                            append(text.substring(boldStart + 2, boldEnd))
                            pop()
                            index = boldEnd + 2
                        } else {
                            append(text, index, text.length)
                            index = text.length
                        }
                    } else {
                        append(text, index, text.length)
                        index = text.length
                    }
                }
            }
        }
    }

private val LINK_REGEX = Regex("\\[([^\\]]+)]\\((https?://[^)\\s]+)\\)")

private val SPAN_BOLD = SpanStyle(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)

private val SPAN_CODE =
    SpanStyle(
        fontFamily = FontFamily.Monospace,
        background =
            androidx.compose.ui.graphics
                .Color(0x14000000),
    )
