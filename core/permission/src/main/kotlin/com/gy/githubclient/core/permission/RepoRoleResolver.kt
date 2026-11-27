/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.permission

/**
 * GitHub 仓库权限布尔组合。
 *
 * 刻意不依赖 feature 层 DTO：数据层只需把 `permissions` 对象映射到这里，
 * 即可复用统一的角色判定与能力评估。
 */
data class RepoPermissionFlags(
    val admin: Boolean = false,
    val maintain: Boolean = false,
    val push: Boolean = false,
    val triage: Boolean = false,
    val pull: Boolean = false,
)

/** 把 GitHub 的权限布尔组合归一到单一角色。 */
object RepoRoleResolver {
    fun resolve(flags: RepoPermissionFlags?): RepoRole {
        if (flags == null) return RepoRole.NONE
        return when {
            flags.admin -> RepoRole.ADMIN
            flags.maintain -> RepoRole.MAINTAIN
            flags.push -> RepoRole.PUSH
            flags.triage -> RepoRole.TRIAGE
            flags.pull -> RepoRole.READ
            else -> RepoRole.NONE
        }
    }
}

/** 角色的中文展示名（UI 徽标用）。 */
val RepoRole.displayLabel: String
    get() =
        when (this) {
            RepoRole.NONE -> "无权限"
            RepoRole.READ -> "只读"
            RepoRole.TRIAGE -> "分类"
            RepoRole.PUSH -> "可推送"
            RepoRole.MAINTAIN -> "维护"
            RepoRole.ADMIN -> "管理员"
        }
