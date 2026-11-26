/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.auth

/**
 * 凭据类型（M3-01 的前置抽象）。
 *
 * **为什么需要它**：不同凭据的能力差异很大，而且**用户看不到这些差异**——
 * 同一个"贡献热力图"入口，classic PAT 能看，fine-grained PAT 一定看不了
 * （GitHub 官方文档明确：GraphQL API 不支持 fine-grained PAT 认证）。
 * 没有这层抽象，App 只能"到点了报 401"，用户完全不知道原因。
 */
enum class CredentialKind {
    /** 细粒度 PAT（`github_pat_` 前缀）。 */
    FINE_GRAINED,

    /** 经典 PAT（`ghp_` / `gho_` / `ghu_` / `ghs_` / `ghr_` 前缀）。 */
    CLASSIC,

    /** OAuth 访问令牌（Device Flow 签发，无特征前缀时按此处理）。 */
    OAUTH,

    /** 无法判定。 */
    UNKNOWN,
    ;

    /** 是否支持 GraphQL（官方限制：fine-grained PAT 不支持 GraphQL 认证）。 */
    val supportsGraphQl: Boolean
        get() = this != FINE_GRAINED && this != UNKNOWN

    /** 是否支持 `/notifications`（官方限制：该端点要求 classic PAT 权限）。 */
    val supportsNotifications: Boolean
        get() = this == CLASSIC

    companion object {
        /** 按令牌前缀判定凭据类型；无法判定时返回 [UNKNOWN]。 */
        fun detect(token: String?): CredentialKind {
            val value = token?.trim().orEmpty()
            if (value.isEmpty()) return UNKNOWN
            return when {
                value.startsWith("github_pat_") -> FINE_GRAINED
                value.startsWith("ghp_") ||
                    value.startsWith("gho_") ||
                    value.startsWith("ghu_") ||
                    value.startsWith("ghs_") ||
                    value.startsWith("ghr_") -> CLASSIC
                // 40 位十六进制的 OAuth 访问令牌
                value.length == 40 && value.all { it.isDigit() || it in 'a'..'f' } -> OAUTH
                else -> UNKNOWN
            }
        }
    }
}

/**
 * 凭据能力项（用于在 UI 上把"不能做"讲清楚，而不是等失败）。
 */
enum class CredentialCapability(
    val label: String,
    /** 该能力依赖的凭据类型。 */
    val requires: CredentialKind,
    /** 不满足时的降级说明（展示给用户）。 */
    val unavailableReason: String,
) {
    /** 通知中心（`/notifications` 需 classic PAT）。 */
    NOTIFICATIONS(
        label = "通知中心",
        requires = CredentialKind.CLASSIC,
        unavailableReason = "该端点仅支持 classic PAT，当前凭据类型无法读取通知",
    ),

    /** 贡献热力图（GraphQL 不支持 fine-grained PAT）。 */
    CONTRIBUTION_HEATMAP(
        label = "贡献热力图",
        requires = CredentialKind.CLASSIC,
        unavailableReason = "GitHub GraphQL 不支持细粒度 PAT 认证，请改用 classic PAT 或 OAuth 登录",
    ),

    /** 仓库写操作（提交/Issue/PR 等）。 */
    REPOSITORY_WRITE(
        label = "仓库写操作",
        requires = CredentialKind.CLASSIC,
        unavailableReason = "当前凭据缺少写权限，请在权限体检页确认所需 scope",
    ),
}

/**
 * 凭据能力求值器（纯函数，可单测）。
 */
object CredentialCapabilityEvaluator {
    /** 该凭据是否具备某能力。 */
    fun supports(
        kind: CredentialKind,
        capability: CredentialCapability,
    ): Boolean = when (capability) {
        CredentialCapability.NOTIFICATIONS -> kind.supportsNotifications
        CredentialCapability.CONTRIBUTION_HEATMAP -> kind.supportsGraphQl
        CredentialCapability.REPOSITORY_WRITE -> kind != CredentialKind.UNKNOWN
    }

    /** 不可用时给用户的原因（可用则返回 null）。 */
    fun unavailableReason(
        kind: CredentialKind,
        capability: CredentialCapability,
    ): String? =
        if (supports(kind, capability)) null else capability.unavailableReason
}
