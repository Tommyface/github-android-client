/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.domain.usecase

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.profile.domain.model.OrgMember
import com.gy.githubclient.feature.profile.domain.model.OrgSummary
import com.gy.githubclient.feature.profile.domain.repository.ProfileRepository

/** 获取组织概要（v0.15.0 A5）。 */
class GetOrgUseCase(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(login: String): NetworkResult<OrgSummary> = repository.getOrg(login)
}

/** 获取组织成员列表（v0.15.0 A5）。 */
class ListOrgMembersUseCase(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(login: String): NetworkResult<List<OrgMember>> = repository.listOrgMembers(login)
}
