/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.presentation

import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.feature.discover.domain.model.MirrorDownloadLink
import com.gy.githubclient.feature.discover.domain.model.ReleaseAsset
import com.gy.githubclient.feature.download.domain.model.DownloadRequest

/**
 * 把 Release 资产构造为下载请求（v0.8.0 M14）。
 * 发现页与多版本列表页共用同一构造逻辑，保证镜像 / 摘要 / 溯源字段一致。
 */
internal fun buildAssetDownloadRequest(
    asset: ReleaseAsset,
    owner: String,
    name: String,
    tag: String,
    link: MirrorDownloadLink,
): DownloadRequest =
    DownloadRequest(
        url = asset.downloadUrl,
        fileName = asset.name.ifBlank { "download.bin" },
        suggestedFileName = asset.name.ifBlank { null },
        repoOwner = owner,
        repoName = name,
        releaseTag = tag,
        assetId = asset.id,
        totalBytes = asset.size,
        useMirror = link.isAccelerated,
        expectedSha256 = asset.sha256,
    )

/** 网络错误 → 中文文案（发现页与多版本列表页共用）。 */
internal fun discoverErrorMessage(error: ApiError): String =
    when (error) {
        ApiError.Unauthorized -> "登录已失效，请重新登录"
        is ApiError.RateLimited -> "请求太频繁，已触发 API 限流，请稍后再试"
        ApiError.NotFound -> "未找到资源"
        is ApiError.Network -> "网络错误：${error.cause}"
        is ApiError.Http -> "请求失败（${error.code}）"
        is ApiError.Unknown -> "未知错误：${error.message}"
    }
