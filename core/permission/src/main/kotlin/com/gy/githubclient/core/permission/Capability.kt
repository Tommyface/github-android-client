/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.permission

/** 仓库角色，按 GitHub 的权限层级从低到高排列。 */
enum class RepoRole(
    val level: Int,
) {
    NONE(0),
    READ(1),
    TRIAGE(2),
    PUSH(3),
    MAINTAIN(4),
    ADMIN(5),
    ;

    fun atLeast(required: RepoRole): Boolean = level >= required.level
}

/**
 * 仓库内的可执行动作。
 *
 * 只描述「需要什么角色/权限」，不绑定具体 API；数据层负责把动作映射到端点。
 */
enum class RepoAction(
    val minimumRole: RepoRole,
    val readOnly: Boolean = false,
) {
    READ_CODE(RepoRole.READ, readOnly = true),
    WRITE_CODE(RepoRole.PUSH),
    WRITE_PROTECTED_BRANCH(RepoRole.PUSH),
    MANAGE_BRANCHES(RepoRole.PUSH),
    READ_ISSUES(RepoRole.READ, readOnly = true),
    WRITE_ISSUES(RepoRole.TRIAGE),
    TRIAGE_ISSUES(RepoRole.TRIAGE),
    READ_PULLS(RepoRole.READ, readOnly = true),
    WRITE_PULLS(RepoRole.PUSH),
    REVIEW_PULLS(RepoRole.READ),
    MERGE_PULLS(RepoRole.PUSH),
    MANAGE_RELEASES(RepoRole.PUSH),
    READ_ACTIONS(RepoRole.READ, readOnly = true),
    RUN_ACTIONS(RepoRole.PUSH),
    MANAGE_ACTIONS_SECRETS(RepoRole.ADMIN),
    MANAGE_REPO_SETTINGS(RepoRole.ADMIN),
    MANAGE_COLLABORATORS(RepoRole.ADMIN),
    MANAGE_WEBHOOKS(RepoRole.ADMIN),
    DELETE_REPO(RepoRole.ADMIN),
}

/** 评估动作时的上下文：角色来自仓库 permissions，scopes 来自 Token 元数据。 */
data class PermissionContext(
    val role: RepoRole,
    /** OAuth/classic PAT 的 scope 列表；fine-grained PAT 为空。 */
    val scopes: Set<String> = emptySet(),
    /**
     * scope 是否可信。
     * fine-grained PAT 没有 scope 元数据，此时为 false，只按角色判断，最终以服务端为准。
     */
    val scopesKnown: Boolean = false,
    val isPrivate: Boolean = true,
    /** 目标分支是否受保护；只影响直接写保护分支，不影响写普通分支。 */
    val defaultBranchProtected: Boolean = false,
)

enum class DenyReason {
    INSUFFICIENT_ROLE,
    MISSING_SCOPE,
    PROTECTED_BRANCH,
}

data class CapabilityDecision(
    val allowed: Boolean,
    val reason: DenyReason? = null,
    val requiredRole: RepoRole? = null,
    val requiredScopes: Set<String> = emptySet(),
)

/** 纯函数能力评估：不触网、不依赖 Android，可直接单测。 */
object CapabilityEvaluator {
    const val SCOPE_REPO = "repo"
    const val SCOPE_PUBLIC_REPO = "public_repo"
    const val SCOPE_DELETE_REPO = "delete_repo"
    const val SCOPE_ADMIN_REPO_HOOK = "admin:repo_hook"

    fun evaluate(
        action: RepoAction,
        context: PermissionContext,
    ): CapabilityDecision {
        if (!context.role.atLeast(action.minimumRole)) {
            return CapabilityDecision(
                allowed = false,
                reason = DenyReason.INSUFFICIENT_ROLE,
                requiredRole = action.minimumRole,
            )
        }
        if (action == RepoAction.WRITE_PROTECTED_BRANCH && context.defaultBranchProtected) {
            return CapabilityDecision(
                allowed = false,
                reason = DenyReason.PROTECTED_BRANCH,
                requiredRole = action.minimumRole,
            )
        }
        val scopes = requiredScopes(action, context)
        if (context.scopesKnown && scopes.isNotEmpty() && scopes.none { it in context.scopes }) {
            return CapabilityDecision(
                allowed = false,
                reason = DenyReason.MISSING_SCOPE,
                requiredRole = action.minimumRole,
                requiredScopes = scopes,
            )
        }
        return CapabilityDecision(allowed = true)
    }

    /**
     * 动作所需的 scope（任一即可）。
     * - 公开仓库的只读动作不需要 scope；
     * - 私有仓库一律需要 repo；
     * - 删除仓库额外需要 delete_repo；
     * - Webhook 允许 repo 或 admin:repo_hook。
     */
    private fun requiredScopes(
        action: RepoAction,
        context: PermissionContext,
    ): Set<String> =
        when {
            action == RepoAction.DELETE_REPO -> setOf(SCOPE_DELETE_REPO)
            action == RepoAction.MANAGE_WEBHOOKS -> setOf(SCOPE_REPO, SCOPE_ADMIN_REPO_HOOK)
            context.isPrivate -> setOf(SCOPE_REPO)
            action.readOnly -> emptySet()
            else -> setOf(SCOPE_REPO, SCOPE_PUBLIC_REPO)
        }
}
