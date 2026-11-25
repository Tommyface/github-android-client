/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain.model

import com.gy.githubclient.core.permission.RepoPermissionFlags
import com.gy.githubclient.core.permission.RepoRole
import com.gy.githubclient.core.permission.RepoRoleResolver

/** GitHub 权限 DTO/领域模型 → 能力模型的桥接。 */
fun RepoPermission.toFlags(): RepoPermissionFlags =
    RepoPermissionFlags(
        admin = admin,
        maintain = maintain,
        push = push,
        triage = triage,
        pull = pull,
    )

/** 当前登录用户在该仓库的角色；无权限信息时视为无角色。 */
val Repository.myRole: RepoRole
    get() = RepoRoleResolver.resolve(permission?.toFlags())
