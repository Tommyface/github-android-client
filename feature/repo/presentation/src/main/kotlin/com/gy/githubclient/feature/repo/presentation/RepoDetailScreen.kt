/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.draw.clip
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
import com.gy.githubclient.feature.repo.domain.model.CheckConclusion
import com.gy.githubclient.feature.repo.domain.model.CheckOverview
import com.gy.githubclient.feature.repo.domain.model.Contributor
import com.gy.githubclient.feature.repo.domain.model.ForkItem
import com.gy.githubclient.feature.repo.domain.model.IssueDetail
import com.gy.githubclient.feature.repo.domain.model.IssueItem
import com.gy.githubclient.feature.repo.domain.model.LanguageShare
import com.gy.githubclient.feature.repo.domain.model.Milestone
import com.gy.githubclient.feature.repo.domain.model.RepoLabel
import com.gy.githubclient.feature.repo.domain.model.Repository
import com.gy.githubclient.feature.repo.domain.model.StarHistory
import com.gy.githubclient.feature.repo.domain.model.StarHistoryPoint
import com.gy.githubclient.feature.repo.domain.model.milestoneProgressPercent
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
    onOpenActions: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    onOpenUpload: () -> Unit = {},
    viewModel: RepoViewModel = koinViewModel(),
) {
    val repo by viewModel.detail.collectAsStateWithLifecycle()
    val branches by viewModel.branches.collectAsStateWithLifecycle()
    val selectedRef by viewModel.selectedRef.collectAsStateWithLifecycle()
    val checkOverview by viewModel.checkOverview.collectAsStateWithLifecycle()
    val readmeState by viewModel.readme.collectAsStateWithLifecycle()
    val starHistory by viewModel.starHistory.collectAsStateWithLifecycle()
    val issues by viewModel.issues.collectAsStateWithLifecycle()
    val issueDetail by viewModel.issueDetail.collectAsStateWithLifecycle()
    val languages by viewModel.languages.collectAsStateWithLifecycle()
    val contributors by viewModel.contributors.collectAsStateWithLifecycle()
    val labels by viewModel.labels.collectAsStateWithLifecycle()
    val milestones by viewModel.milestones.collectAsStateWithLifecycle()
    val forks by viewModel.forks.collectAsStateWithLifecycle()

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
                    Text("⬇ 下载 ZIP（$selectedRef）")
                }
                OutlinedButton(onClick = onOpenReleases, modifier = Modifier.fillMaxWidth()) {
                    Text("📦 Releases / Tags")
                }
                OutlinedButton(onClick = { onOpenFiles(selectedRef) }, modifier = Modifier.fillMaxWidth()) {
                    Text("📂 浏览文件（$selectedRef）")
                }
                OutlinedButton(onClick = onOpenCommits, modifier = Modifier.fillMaxWidth()) {
                    Text("🕘 提交历史 / Diff")
                }
                OutlinedButton(onClick = onOpenActions, modifier = Modifier.fillMaxWidth()) {
                    Text("⚙ Actions 运行记录")
                }
                OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.repo_settings_entry))
                }
                OutlinedButton(onClick = onOpenUpload, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.upload_file_entry))
                }
            }
        }

        item {
            StarTrendCard(starHistory = starHistory)
        }

        item {
            CheckCard(overview = checkOverview, ref = selectedRef)
        }

        item {
            RepoInfoCard(repo = current)
        }

        item {
            LanguageCard(languages = languages)
        }

        item {
            ContributorCard(contributors = contributors)
        }

        item {
            LabelCard(labels = labels)
        }

        item {
            MilestoneCard(milestones = milestones)
        }

        item {
            ForkCard(forks = forks)
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
            Text("分支（${branches.size}）· 点击切换当前分支", style = MaterialTheme.typography.titleMedium)
        }
        items(branches, key = { "branch_${it.name}" }) { branch ->
            val isSelected = branch.name == selectedRef
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectBranch(branch.name) },
                colors =
                    if (isSelected) {
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    } else {
                        CardDefaults.cardColors()
                    },
            ) {
                Row(Modifier.padding(12.dp)) {
                    Text(
                        if (isSelected) "✓ ${branch.name}" else branch.name,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
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

/**
 * CI 检查卡片（v0.13.0 A2）：只读展示当前 ref 的 check runs + statuses。
 * 全部来自 REST，结论归一化后着色；无数据明示「该分支暂无 CI 记录」，不臆造。
 */
@Composable
internal fun CheckCard(
    overview: CheckOverview?,
    ref: String,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("CI 检查状态（$ref）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            when {
                overview == null ->
                    Text("加载中…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                overview.isEmpty ->
                    Text(
                        "该分支暂无 CI 检查记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                else -> {
                    Text("总体：${conclusionLabel(overview.overall)}", style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(4.dp))
                    overview.runs.forEach { run -> CheckLine(name = run.name, conclusion = run.normalized) }
                    overview.statuses.forEach { status -> CheckLine(name = status.context, conclusion = status.normalized) }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "数据来源：GitHub REST · check-runs / statuses（只读）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * 仓库信息卡（v0.15.0 A7，只读）：复用详情已加载的 [Repository] 字段做只读展示，
 * 不引入任何 PATCH 写操作，保持「发现–评估–下载」定位。
 */
@Composable
private fun RepoInfoCard(repo: Repository) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("仓库信息（只读）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth()) {
                Text("可见性", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                Text(
                    when {
                        repo.isPrivate -> "私有"
                        else -> "公开"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            InfoLine(label = "默认分支", value = repo.defaultBranch)
            InfoLine(label = "语言", value = repo.language ?: "未标注")
            InfoLine(label = "许可证", value = repo.license?.name ?: "无")
            InfoLine(label = "Fork", value = if (repo.isFork) "是" else "否")
            InfoLine(label = "已归档", value = if (repo.isArchived) "是" else "否")
            if (repo.topics.isNotEmpty()) {
                InfoLine(label = "Topics", value = repo.topics.joinToString(", "))
            }
            repo.permission?.let { perm ->
                InfoLine(
                    label = "我的权限",
                    value =
                        buildList {
                            if (perm.admin) add("管理")
                            if (perm.push) add("写入")
                            if (perm.pull) add("读取")
                        }.ifEmpty { listOf("读取") }.joinToString("/"),
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "数据来源：GitHub REST · 仓库详情字段（只读，未做任何修改）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun InfoLine(
    label: String,
    value: String,
) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall)
    }
}

/** 语言构成卡（v0.16.0 P2-b，只读）：按字节占比排序，逐条显示语言与百分比。 */
@Composable
private fun LanguageCard(languages: List<LanguageShare>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("语言构成", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (languages.isEmpty()) {
                Text("暂无语言统计数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                // 占比条：每段宽度按 percent 加权，颜色在主题主色系内循环区分。
                Row(Modifier.fillMaxWidth().height(8.dp)) {
                    languages.forEachIndexed { index, share ->
                        Spacer(
                            Modifier
                                .weight(share.percent.coerceAtLeast(0.1).toFloat())
                                .height(8.dp)
                                .background(languageBarColor(index)),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                languages.forEach { share ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
                        Text(share.language, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("${share.percent}%", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "数据来源：GitHub REST · languages（按字节数占比，只读）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun languageBarColor(index: Int): androidx.compose.ui.graphics.Color {
    val scheme = MaterialTheme.colorScheme
    return listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primaryContainer, scheme.secondaryContainer)[
        index % 5,
    ]
}

/** 贡献者卡（v0.16.0 P2-c，只读）：头像 + login + 贡献数。 */
@Composable
private fun ContributorCard(contributors: List<Contributor>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("贡献者（${contributors.size}）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (contributors.isEmpty()) {
                Text("暂无贡献者数据", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                contributors.take(20).forEach { contributor ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        val avatarUrl = contributor.avatarUrl
                        if (avatarUrl.isNullOrBlank()) {
                            Surface(
                                modifier = Modifier.size(32.dp).clip(CircleShape),
                                color = MaterialTheme.colorScheme.primaryContainer,
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(contributor.login.take(1).uppercase(), style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        } else {
                            AsyncImage(
                                model = avatarUrl,
                                contentDescription = contributor.login,
                                modifier = Modifier.size(32.dp).clip(CircleShape),
                            )
                        }
                        Spacer(Modifier.padding(4.dp))
                        Text("@${contributor.login}", style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("${contributor.contributions} 次", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "数据来源：GitHub REST · contributors（只读，最多显示 20 位）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 仓库标签卡（v0.17.0 P3-a，只读）：颜色 chip + 标签名，无标签时明示。 */
@Composable
private fun LabelCard(labels: List<RepoLabel>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("标签（${labels.size}）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (labels.isEmpty()) {
                Text("该仓库暂无标签", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                labels.take(LABEL_PREVIEW_LIMIT).forEach { label ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            modifier = Modifier.size(12.dp).clip(CircleShape),
                            color = parseLabelColor(label.color),
                        ) {}
                        Spacer(Modifier.padding(4.dp))
                        Text(label.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        label.description?.let {
                            if (it.isNotBlank()) {
                                Text(
                                    it.take(24),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "数据来源：GitHub REST · labels（只读）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 里程碑卡（v0.17.0 P3-b，只读）：状态 + 完成度进度条 + closed/open issue 计数。 */
@Composable
private fun MilestoneCard(milestones: List<Milestone>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("里程碑（${milestones.size}）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (milestones.isEmpty()) {
                Text("该仓库暂无里程碑", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                milestones.take(MILESTONE_PREVIEW_LIMIT).forEach { ms ->
                    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Text(
                            "#${ms.number} ${ms.title}（${if (ms.isOpen) "开放" else "已关闭"}）",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        val progress = milestoneProgressPercent(ms.openIssues, ms.closedIssues)
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        )
                        Text(
                            "完成度 $progress% · ${ms.closedIssues}/${ms.totalIssues} issue 已完成",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "数据来源：GitHub REST · milestones（只读）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Fork 列表卡（v0.17.0 P3-c，只读）：派生仓库全名 + owner + star 数，最多显示 10 位。 */
@Composable
private fun ForkCard(forks: List<ForkItem>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("Fork（${forks.size}）", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            if (forks.isEmpty()) {
                Text("该仓库暂无 Fork", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                forks.take(FORK_PREVIEW_LIMIT).forEach { fork ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(fork.fullName, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                        Text("★ ${fork.stargazersCount}", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "数据来源：GitHub REST · forks（只读，最多显示 10 个）",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 把标签 6 位十六进制色解析为 Compose 颜色；非法值回退到主色容器色。 */
@Composable
private fun parseLabelColor(hex: String): androidx.compose.ui.graphics.Color {
    val fallback = MaterialTheme.colorScheme.primaryContainer
    val raw = hex.trim().removePrefix("#")
    return if (raw.length == 6) {
        runCatching {
            androidx.compose.ui.graphics
                .Color(0xFF000000L or raw.toLong(16))
        }.getOrDefault(fallback)
    } else {
        fallback
    }
}

private const val LABEL_PREVIEW_LIMIT = 30
private const val MILESTONE_PREVIEW_LIMIT = 10
private const val FORK_PREVIEW_LIMIT = 10

@Composable
private fun CheckLine(
    name: String,
    conclusion: CheckConclusion,
) {
    Row(Modifier.padding(vertical = 2.dp)) {
        Text(
            "${conclusionIcon(conclusion)} $name",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        Text(conclusionLabel(conclusion), style = MaterialTheme.typography.labelSmall)
    }
}

internal fun conclusionIcon(conclusion: CheckConclusion): String =
    when (conclusion) {
        CheckConclusion.SUCCESS -> "✓"
        CheckConclusion.FAILURE -> "✕"
        CheckConclusion.NEUTRAL -> "–"
        CheckConclusion.PENDING -> "◷"
        CheckConclusion.UNKNOWN -> "?"
    }

internal fun conclusionLabel(conclusion: CheckConclusion): String =
    when (conclusion) {
        CheckConclusion.SUCCESS -> "通过"
        CheckConclusion.FAILURE -> "失败"
        CheckConclusion.NEUTRAL -> "中性"
        CheckConclusion.PENDING -> "进行中"
        CheckConclusion.UNKNOWN -> "未知"
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
    // 主题令牌在 Composable 上下文取一次，再传入纯函数 renderInline
    // （renderInline / buildAnnotatedString 不是 @Composable，不能直接读 MaterialTheme）。
    val codeBackground = MaterialTheme.colorScheme.surfaceVariant
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
                remember(block.text, codeBackground) { renderInline(block.text, codeBackground) },
                style = MaterialTheme.typography.bodyMedium,
            )

        is MdBlock.ListItem ->
            Row(Modifier.padding(start = 8.dp)) {
                Text("• ", style = MaterialTheme.typography.bodyMedium)
                Text(
                    remember(block.text, codeBackground) { renderInline(block.text, codeBackground) },
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
internal fun renderInline(
    text: String,
    codeBackground: androidx.compose.ui.graphics.Color? = null,
): AnnotatedString =
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
                        pushStyle(spanCode(codeBackground))
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

/**
 * 代码片段样式：背景由调用方传入**主题令牌**（不写死色值，见 konsist 规则 14）。
 *
 * 之所以不直接在这里取 MaterialTheme：调用点在 `buildAnnotatedString` 内（非 @Composable 上下文），
 * 因此颜色由 Composable 侧取好后传入。
 */
private fun spanCode(background: androidx.compose.ui.graphics.Color?): SpanStyle =
    if (background == null) {
        SpanStyle(fontFamily = FontFamily.Monospace)
    } else {
        SpanStyle(
            fontFamily = FontFamily.Monospace,
            background = background,
        )
    }
