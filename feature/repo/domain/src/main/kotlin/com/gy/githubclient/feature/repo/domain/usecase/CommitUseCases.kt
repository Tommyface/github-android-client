/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain.usecase

import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.domain.model.CommitDetail
import com.gy.githubclient.feature.repo.domain.model.CommitSummary
import com.gy.githubclient.feature.repo.domain.repository.RepoRepository

/** 提交历史列表（v0.12.0 N10）：page 从 1 开始。 */
class ListCommitsUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
        page: Int = 1,
        perPage: Int = DEFAULT_PER_PAGE,
    ): NetworkResult<List<CommitSummary>> = repository.listCommits(owner, name, page, perPage)

    private companion object {
        const val DEFAULT_PER_PAGE = 30
    }
}

/** 单次提交详情（含变更文件 patch，v0.12.0 N10）。 */
class GetCommitDetailUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
        sha: String,
    ): NetworkResult<CommitDetail> = repository.getCommitDetail(owner, name, sha)
}
