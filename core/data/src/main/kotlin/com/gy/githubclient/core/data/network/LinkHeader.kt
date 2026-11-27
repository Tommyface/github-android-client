/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.data.network

/**
 * `Link` 响应头解析（RFC 8288，GitHub 分页用）。
 *
 * 形如：
 * ```
 * <https://api.github.com/user/repos?per_page=100&page=2>; rel="next",
 *   <https://api.github.com/user/repos?per_page=100&page=5>; rel="last"
 * ```
 *
 * 纯函数，无 Android / Ktor 依赖，便于单测。
 */
object LinkHeader {
    /** 取指定 rel 的目标 URL；不存在返回 null。 */
    fun rel(
        header: String?,
        relation: String,
    ): String? {
        if (header.isNullOrBlank()) return null
        val wanted = relation.lowercase()
        // 以逗号分隔，但逗号可能出现在 URL 查询串里，因此按 "<" 切分更稳。
        return header
            .split('<')
            .drop(1)
            .mapNotNull { chunk ->
                val end = chunk.indexOf('>')
                if (end <= 0) return@mapNotNull null
                val url = chunk.substring(0, end).trim()
                val rest = chunk.substring(end + 1)
                val relMatch = REL_PATTERN.find(rest) ?: return@mapNotNull null
                if (relMatch.groupValues[1].lowercase() == wanted) url else null
            }.firstOrNull()
    }

    /** 下一页 URL（rel="next"）。 */
    fun next(header: String?): String? = rel(header, "next")

    private val REL_PATTERN = Regex("""rel\s*=\s*"?([A-Za-z]+)"?""")
}
