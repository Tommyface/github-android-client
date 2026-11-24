/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.data

import com.gy.githubclient.feature.auth.data.remote.GitHubPermissionApi
import com.gy.githubclient.feature.auth.data.remote.ProbeResult
import com.gy.githubclient.feature.auth.domain.model.AuthMethod
import com.gy.githubclient.feature.auth.domain.model.Capability
import com.gy.githubclient.feature.auth.domain.model.PermissionReport
import com.gy.githubclient.feature.auth.domain.repository.AuthRepository
import com.gy.githubclient.feature.auth.domain.repository.PermissionAuditRepository

/**
 * 权限体检实现：按凭证类型选择判定策略。
 * - OAuth：登录时保存的 scope 直接映射（零探测）。
 * - classic PAT：/user 响应头 x-oauth-scopes 提供真实 scope，映射即可。
 * - fine-grained PAT：无 scope 元数据，对代表端点探测（读语义），写权限提示以 GitHub 后台为准。
 */
@Suppress("TooGenericExceptionCaught")
class PermissionAuditRepositoryImpl(
    private val authRepository: AuthRepository,
    private val api: GitHubPermissionApi,
) : PermissionAuditRepository {
    override suspend fun audit(): PermissionReport {
        val token = authRepository.getPrimaryToken()
        val method = authRepository.currentSession().method
        if (token == null) {
            return PermissionReport(
                method = method,
                login = null,
                scopes = emptyList(),
                capabilities = emptyList(),
            )
        }

        val userProbe = api.probeUser(token)
        val login = (userProbe as? ProbeResult.Granted)?.login

        val scopes: List<String> =
            when {
                method == AuthMethod.OAUTH -> authRepository.currentSession().scopes
                token.startsWith(PREFIX_CLASSIC) -> api.oauthScopesFromHeader(token)
                else -> emptyList()
            }

        val capabilities =
            if (scopes.isNotEmpty()) {
                mapFromScopes(scopes, if (method == AuthMethod.OAUTH) BASIS_OAUTH_SCOPE else BASIS_CLASSIC_SCOPE)
            } else {
                probeCapabilities(token, userProbe)
            }

        return PermissionReport(
            method = method,
            login = login,
            scopes = scopes,
            capabilities = capabilities,
        )
    }

    /** fine-grained PAT / 头信息缺失：端点探测（读语义）。 */
    private suspend fun probeCapabilities(
        token: String,
        userProbe: ProbeResult,
    ): List<Capability> {
        val userGranted = userProbe is ProbeResult.Granted
        val starredProbe = api.probeStarred(token)
        val notificationsProbe = api.probeNotifications(token)
        return listOf(
            Capability(
                name = "账号信息",
                features = "个人主页 / 头像 / 权限体检",
                granted = userGranted,
                basis = basisOf(userProbe),
            ),
            Capability(
                name = "星标收藏",
                features = "搜索结果收藏 / 详情页 Star",
                granted = starredProbe is ProbeResult.Granted,
                basis = basisOf(starredProbe) + READ_ONLY_SUFFIX,
            ),
            Capability(
                name = "仓库读写",
                features = "搜索 / 详情 / Fork / 克隆下载",
                granted = starredProbe is ProbeResult.Granted,
                basis = basisOf(starredProbe) + "; 写权限以 GitHub 后台设置为准",
            ),
            Capability(
                name = "通知读取",
                features = "通知类功能",
                granted = notificationsProbe is ProbeResult.Granted,
                basis = basisOf(notificationsProbe),
            ),
        )
    }

    /** OAuth / classic PAT：scope 集合直接映射功能面。 */
    private fun mapFromScopes(
        scopes: List<String>,
        basisPrefix: String,
    ): List<Capability> =
        listOf(
            Capability(
                name = "账号信息",
                features = "个人主页 / 头像 / 权限体检",
                granted = "read:user" in scopes || "user" in scopes,
                basis = "$basisPrefix read:user",
            ),
            Capability(
                name = "仓库读写",
                features = "搜索 / 详情 / Fork / 克隆下载",
                granted = "repo" in scopes || "public_repo" in scopes,
                basis = "$basisPrefix repo",
            ),
            Capability(
                name = "星标收藏",
                features = "搜索结果收藏 / 详情页 Star",
                granted = "repo" in scopes || "public_repo" in scopes,
                basis = "$basisPrefix repo（含 star 写权限）",
            ),
            Capability(
                name = "通知读取",
                features = "通知类功能",
                granted = "notifications" in scopes,
                basis = "$basisPrefix notifications",
            ),
            Capability(
                name = "邮箱读取",
                features = "账号邮箱展示",
                granted = "user:email" in scopes || "user" in scopes,
                basis = "$basisPrefix user:email",
            ),
            Capability(
                name = "Actions 工作流",
                features = "Actions 触发与状态",
                granted = "workflow" in scopes,
                basis = "$basisPrefix workflow",
            ),
        )

    private fun basisOf(probe: ProbeResult): String =
        when (probe) {
            is ProbeResult.Granted -> "端点探测 200"
            ProbeResult.Denied -> "端点探测 401/403"
            ProbeResult.RateLimited -> "端点限流，结果仅供参考"
            ProbeResult.NetworkError -> "网络异常，未能判定"
        }

    private companion object {
        const val PREFIX_CLASSIC = "ghp_"
        const val BASIS_OAUTH_SCOPE = "OAuth scope:"
        const val BASIS_CLASSIC_SCOPE = "Token 声明 scope:"
        const val READ_ONLY_SUFFIX = "（读探测）"
    }
}
