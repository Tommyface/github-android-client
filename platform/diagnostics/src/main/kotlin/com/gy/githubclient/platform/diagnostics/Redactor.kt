/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.platform.diagnostics

/**
 * 日志脱敏器（M1-07）。
 *
 * 目标：**任何写进日志的文本，都不能包含可直接使用的凭据或私有路径**。
 *
 * 脱敏规则（按顺序执行，幂等）：
 *  1. `Authorization: Bearer xxx` / `token=xxx` / `access_token=xxx` → 只保留前 4 位；
 *  2. GitHub 令牌形态（`ghp_` / `gho_` / `ghu_` / `ghs_` / `ghr_` / `github_pat_`）→ 中段打码；
 *  3. `https://user:pass@host` → 去掉用户名口令；
 *  4. 绝对路径中的用户名目录（`/Users/<name>/`、`/home/<name>/`）→ 打码。
 *
 * 纯 Kotlin、无 Android 依赖，便于穷举测试。
 */
object Redactor {
    private val TOKEN_PATTERNS =
        listOf(
            Regex("""(?i)(authorization\s*[:=]\s*bearer\s+)([A-Za-z0-9._\-]+)"""),
            Regex("""(?i)((?:access_|refresh_)?token\s*[:=]\s*)([A-Za-z0-9._\-]+)"""),
            Regex("""(?i)(secret|password|passwd|pwd)\s*[:=]\s*([^\s,;&]+)"""),
        )

    private val GITHUB_TOKEN = Regex("""\b(gh[pousr]_[A-Za-z0-9]{16,}|github_pat_[A-Za-z0-9_]{20,})""")

    private val URL_CREDENTIALS = Regex("""(https?://)[^/\s:@]+:[^/\s@]+@""")

    private val USER_HOME = Regex("""(/Users/|/home/)([^/\s"']+)""")

    /** 脱敏入口。 */
    fun redact(text: String): String {
        var result = text
        TOKEN_PATTERNS.forEach { pattern ->
            result =
                pattern.replace(result) { match ->
                    val prefix = match.groupValues[1]
                    val value = match.groupValues[2]
                    prefix + maskValue(value)
                }
        }
        result = GITHUB_TOKEN.replace(result) { match -> maskValue(match.groupValues[1]) }
        result = URL_CREDENTIALS.replace(result) { match -> match.groupValues[1] + "***:***@" }
        result = USER_HOME.replace(result) { match -> match.groupValues[1] + "***" }
        return result
    }

    /**
     * 保留前 4 位与末 2 位，其余打码；过短的值整体打码（不泄露任何信息）。
     *
     * 已打码的值（含 `***`）原样返回——保证脱敏**幂等**：
     * 同一段文本反复脱敏不会越打越短，也不会把 `***` 再切一次。
     */
    fun maskValue(value: String): String =
        when {
            value.contains("***") -> value
            value.length <= 8 -> "***"
            else -> value.take(4) + "***" + value.takeLast(2)
        }
}
