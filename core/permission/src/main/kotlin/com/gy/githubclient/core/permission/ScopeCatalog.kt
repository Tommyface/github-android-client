/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.permission

/**
 * App 的写能力面向 OAuth scope 的分组。
 *
 * 一个功能面可能被多个 scope 满足（如 repo 同时覆盖读写），
 * [anyOf] 中任意一个存在即视为可用。
 */
enum class ScopeFeature(
    val title: String,
    val purpose: String,
    val anyOf: Set<String>,
) {
    ACCOUNT_READ(
        title = "账号信息",
        purpose = "读取登录名、头像与公开资料",
        anyOf = setOf("read:user", "user"),
    ),
    REPO_READ(
        title = "仓库读取",
        purpose = "浏览公开/私有仓库、代码、Issue 与 PR",
        anyOf = setOf("repo", "public_repo"),
    ),
    REPO_WRITE(
        title = "仓库写入",
        purpose = "提交文件、管理分支、Issue/PR 与仓库设置",
        anyOf = setOf("repo", "public_repo"),
    ),
    NOTIFICATIONS(
        title = "通知",
        purpose = "读取通知、标记已读与管理线程订阅",
        anyOf = setOf("notifications", "repo"),
    ),
    WORKFLOW(
        title = "Actions",
        purpose = "查看、触发与重跑工作流",
        anyOf = setOf("workflow"),
    ),
    GIST(
        title = "Gist",
        purpose = "创建、编辑与删除 Gist",
        anyOf = setOf("gist"),
    ),
    ORG_READ(
        title = "组织",
        purpose = "读取组织信息、成员与团队",
        anyOf = setOf("read:org", "repo"),
    ),
    FOLLOW(
        title = "关注",
        purpose = "关注或取关其他用户",
        anyOf = setOf("user:follow"),
    ),
}

/** 单个功能面的 scope 判定结果。 */
data class ScopeStatus(
    val feature: ScopeFeature,
    val granted: Boolean,
    /** 实际命中的 scope（未授予时为 null）。 */
    val matchedScope: String? = null,
    /** 该功能面需要的 scope（展示用途）。 */
    val requiredScopes: Set<String>,
)

/**
 * Scope 目录与自省工具。
 *
 * 负责把 GitHub 返回的 scope 原文映射成用户可理解的功能面，
 * 并给出「缺哪些 scope」的可执行指引；不发起任何网络请求。
 */
object ScopeCatalog {
    /** 按功能面顺序评估当前 scope 集合。 */
    fun assess(granted: Collection<String>): List<ScopeStatus> {
        val normalized = granted.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return ScopeFeature.entries.map { feature ->
            val matched = feature.anyOf.firstOrNull { it in normalized }
            ScopeStatus(
                feature = feature,
                granted = matched != null,
                matchedScope = matched,
                requiredScopes = feature.anyOf,
            )
        }
    }

    /**
     * 当前 scope 集合缺少的功能面所需 scope（展开并去重，保持枚举顺序）。
     * 已授予的功能面不会出现在结果中。
     */
    fun missingScopes(granted: Collection<String>): List<String> {
        val normalized = granted.map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        return ScopeFeature.entries
            .filter { feature -> feature.anyOf.none { it in normalized } }
            .map { it.anyOf.first() }
            .distinct()
    }

    /** 统一展示的 scope 摘要，如 "repo · notifications"；空集合返回 "无"。 */
    fun summary(granted: Collection<String>): String =
        granted
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .joinToString(" · ")
            .ifEmpty { "无" }

    /** 写能力需要的最小 scope 集合（用于首次授权说明）。 */
    val recommendedScopes: List<String> =
        listOf("repo", "read:user", "user:email", "workflow", "notifications", "gist", "read:org", "user:follow")
}
