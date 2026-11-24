/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.domain.usecase

import com.gy.githubclient.feature.auth.domain.model.PermissionReport
import com.gy.githubclient.feature.auth.domain.repository.PermissionAuditRepository

/** 执行权限体检：返回当前凭证「能做什么 / 缺什么」。 */
class AuditPermissionsUseCase(
    private val repository: PermissionAuditRepository,
) {
    suspend operator fun invoke(): PermissionReport = repository.audit()
}
