/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain.model

/** 提交列表项（v0.12.0 N10）：GET /repos/{o}/{r}/commits。 */
data class CommitSummary(
    val sha: String,
    val message: String,
    val authorName: String?,
    val authorDate: String?,
    val htmlUrl: String,
) {
    /** 列表展示用的短 SHA（7 位）；不足 7 位时原样返回。 */
    val shortSha: String get() = sha.take(7)

    /** 多行 commit message 的第一行。 */
    val title: String get() =
        message
            .lineSequence()
            .firstOrNull()
            .orEmpty()
            .ifBlank { shortSha }
}

/** 变更文件（v0.12.0 N10）：单次提交详情里的 files[]。 */
data class CommitFile(
    val filename: String,
    val status: String,
    val additions: Int,
    val deletions: Int,
    val patch: String?,
) {
    val isBinary: Boolean get() = patch == null
}

/** 单次提交详情（v0.12.0 N10）：GET /repos/{o}/{r}/commits/{sha}。 */
data class CommitDetail(
    val sha: String,
    val message: String,
    val authorName: String?,
    val authorDate: String?,
    val htmlUrl: String,
    val files: List<CommitFile>,
) {
    val shortSha: String get() = sha.take(7)
    val title: String get() =
        message
            .lineSequence()
            .firstOrNull()
            .orEmpty()
            .ifBlank { shortSha }
    val totalAdditions: Int get() = files.sumOf { it.additions }
    val totalDeletions: Int get() = files.sumOf { it.deletions }
}

/**
 * 统一 diff 补丁解析（纯函数，可单测）：把 `@@ -a,b +c,d @@` 之后的行按前缀分类。
 * `+` 新增、`-` 删除、` ` 上下文；头行与 `\\ No newline` 标记保留为上下文。
 * 只做展示着色，不构造完整 diff 状态机。
 */
enum class DiffLineKind {
    ADD,
    DELETE,
    CONTEXT,
    HEADER,
}

data class DiffLine(
    val text: String,
    val kind: DiffLineKind,
)

fun parseDiffPatch(patch: String): List<DiffLine> {
    // 空串在 Kotlin lineSequence 中仍产生一个空行，不视为有效 diff 行。
    if (patch.isEmpty()) return emptyList()
    return patch
        .lineSequence()
        .map { line ->
            val kind =
                when {
                    line.startsWith("@@") -> DiffLineKind.HEADER
                    line.startsWith("+") -> DiffLineKind.ADD
                    line.startsWith("-") -> DiffLineKind.DELETE
                    else -> DiffLineKind.CONTEXT
                }
            DiffLine(line, kind)
        }.toList()
}
