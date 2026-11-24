/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain.model

/** 递归文件树节点（v0.16.0 P2-a）：来自 GET /git/trees/{ref}?recursive=1。 */
data class TreeNode(
    val path: String,
    val type: String,
    val size: Long,
    val sha: String,
) {
    val isDirectory: Boolean get() = type == "tree"
    val isFile: Boolean get() = type == "blob"
}

/** 递归文件树结果：节点列表 + JSON 端点是否截断（truncated=true 时节点不全）。 */
data class RepoTree(
    val nodes: List<TreeNode>,
    val isTruncated: Boolean = false,
) {
    val fileCount: Int get() = nodes.count { it.isFile }
    val dirCount: Int get() = nodes.count { it.isDirectory }
}

/** 语言构成单条（v0.16.0 P2-b）：字节数 + 归一化百分比。 */
data class LanguageShare(
    val language: String,
    val bytes: Long,
    val percent: Double,
)

/** 贡献者（v0.16.0 P2-c）：来自 GET /contributors。 */
data class Contributor(
    val login: String,
    val avatarUrl: String?,
    val htmlUrl: String,
    val contributions: Int,
) {
    companion object {
        const val AVATAR_SIZE = 80
    }
}

/** 语言字节占比计算：按字节降序，百分比保留一位小数，总和归一。 */
fun languageShares(raw: Map<String, Long>): List<LanguageShare> {
    val total = raw.values.filter { it > 0 }.sum()
    if (total <= 0) return emptyList()
    return raw
        .filter { it.value > 0 }
        .toList()
        .sortedByDescending { it.second }
        .map { (language, bytes) ->
            LanguageShare(
                language = language,
                bytes = bytes,
                percent = (bytes * 100.0 / total * 10.0).toLong() / 10.0,
            )
        }
}
