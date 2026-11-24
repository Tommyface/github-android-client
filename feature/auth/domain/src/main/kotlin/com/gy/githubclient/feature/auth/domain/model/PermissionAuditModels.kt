/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.domain.model

/** 单项能力体检结果：本 App 的一个功能面对当前凭证是否可用。 */
data class Capability(
    /** 功能面名称，如「仓库读写」。 */
    val name: String,
    /** 覆盖的 App 功能，如「搜索 / 星标 / 收藏 / Fork / 下载」。 */
    val features: String,
    /** 是否可用。 */
    val granted: Boolean,
    /** 判定依据：「OAuth scope: repo」/「端点探测 200」/「端点探测 403」等。 */
    val basis: String,
)

/** 权限体检报告。 */
data class PermissionReport(
    /** 凭证类型。 */
    val method: AuthMethod?,
    /** 账号 login（探测失败时为 null）。 */
    val login: String?,
    /**
     * OAuth：GitHub 实际授予的 scope 原文列表。
     * classic PAT：来自 /user 响应头 x-oauth-scopes。
     * fine-grained PAT：为空，以 [capabilities] 探测结果为准。
     */
    val scopes: List<String>,
    val capabilities: List<Capability>,
) {
    val grantedCount: Int get() = capabilities.count { it.granted }
}
