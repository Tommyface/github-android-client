/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain.usecase

import com.gy.githubclient.core.mutation.MutationResult
import com.gy.githubclient.feature.repo.domain.model.CreatedRepository
import com.gy.githubclient.feature.repo.domain.model.UploadTarget
import com.gy.githubclient.feature.repo.domain.repository.RepoRepository

/**
 * 修改仓库设置（M2-05）。
 *
 * 可见性与归档属于**危险操作**：调用方（UI）必须先经 [com.gy.githubclient.core.presentation.components.DangerConfirmSheet]，
 * 且使用方必须在 `MutationSpec.dangerous` 上被标记（数据层已标记）。
 */
class UpdateRepoSettingsUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
        description: String? = null,
        private: Boolean? = null,
        archived: Boolean? = null,
    ): MutationResult<Unit> = repository.updateRepoSettings(owner, name, description, private, archived)
}

/** 删除仓库（不可撤销；UI 需二次确认并要求输入仓库名）。 */
class DeleteRepositoryUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
    ): MutationResult<Unit> = repository.deleteRepository(owner, name)
}

/** 创建仓库（不可幂等：失败不自动重试，避免重复建库）。 */
class CreateRepositoryUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        name: String,
        description: String?,
        private: Boolean,
    ): MutationResult<CreatedRepository> = repository.createRepository(name, description, private)
}

/**
 * 上传文件到仓库（M2-08）。
 *
 * 路径必须在数据层做**白名单与规范化**校验（禁止 `..` 穿越与绝对路径），
 * 否则一次上传就可能改到仓库之外的位置。
 */
class UploadFileUseCase(
    private val repository: RepoRepository,
) {
    suspend operator fun invoke(
        owner: String,
        name: String,
        target: UploadTarget,
        contentBase64: String,
        message: String,
    ): MutationResult<Unit> = repository.uploadFile(owner, name, target, contentBase64, message)
}

/**
 * 上传目标路径的**纯逻辑校验**（可单测）。
 *
 * 规则：
 *  - 不得为空；
 *  - 不得是绝对路径（以 `/` 开头）；
 *  - 不得包含 `..` 段（防止路径穿越到仓库之外）；
 *  - 不得包含空段（`//`）。
 */
object UploadPathValidator {
    sealed class Result {
        data class Valid(
            val normalizedPath: String,
        ) : Result()

        data class Invalid(
            val reason: String,
        ) : Result()
    }

    fun validate(rawPath: String): Result {
        val path = rawPath.trim().removePrefix("./")
        if (path.isBlank()) return Result.Invalid("路径不能为空")
        if (path.startsWith("/")) return Result.Invalid("路径必须是仓库内的相对路径")
        val segments = path.split('/')
        if (segments.any { it.isEmpty() }) return Result.Invalid("路径不能包含空目录")
        if (segments.any { it == ".." }) return Result.Invalid("路径不能包含 .. （路径穿越）")
        if (segments.any { it == "." }) return Result.Invalid("路径不能包含 .")
        return Result.Valid(segments.joinToString("/"))
    }
}
