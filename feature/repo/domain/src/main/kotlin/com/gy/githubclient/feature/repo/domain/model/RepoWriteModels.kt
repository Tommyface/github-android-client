/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain.model

/** 创建仓库后的结果（只取 App 需要的字段）。 */
data class CreatedRepository(
    val fullName: String,
    val htmlUrl: String,
    val private: Boolean,
)

/**
 * 上传目标（仓库 + 路径 + 分支）。
 *
 * 路径在写入前必须经 [com.gy.githubclient.feature.repo.domain.usecase.UploadPathValidator] 校验，
 * 禁止路径穿越。
 */
data class UploadTarget(
    val owner: String,
    val repository: String,
    val path: String,
    val branch: String? = null,
)
