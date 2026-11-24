/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.domain.repository

import com.gy.githubclient.feature.auth.domain.model.PermissionReport

/** 权限体检仓库：判定当前主通道凭证对各功能面的可用性。 */
interface PermissionAuditRepository {
    /**
     * 执行体检。
     * - OAuth：直接用保存的 scope 映射（零网络探测），仍调 /user 校验 token 有效性。
     * - classic PAT：从 /user 响应头 x-oauth-scopes 读取 scope 后映射。
     * - fine-grained PAT：对代表端点逐一探测（200=授予 / 403=未授予）。
     */
    suspend fun audit(): PermissionReport
}
