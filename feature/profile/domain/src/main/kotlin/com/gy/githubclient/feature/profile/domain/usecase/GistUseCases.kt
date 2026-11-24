/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.domain.usecase

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.profile.domain.model.GistItem
import com.gy.githubclient.feature.profile.domain.repository.ProfileRepository

/** 获取用户 Gist 列表（v0.15.0 A6）。 */
class ListUserGistsUseCase(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(login: String): NetworkResult<List<GistItem>> = repository.listUserGists(login)
}

/** 获取单条 Gist 详情（含文件正文，v0.15.0 A6）。 */
class GetGistUseCase(
    private val repository: ProfileRepository,
) {
    suspend operator fun invoke(id: String): NetworkResult<GistItem> = repository.getGist(id)
}
