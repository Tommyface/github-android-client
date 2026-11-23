/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.domain.model

/** 商店发现分类，用于筛选精选应用。 */
enum class DiscoverCategory {
    ALL,
    ANDROID,
    DEVELOPER,
    MEDIA,
    UTILITY,
    GAMES,
    ;

    companion object {
        fun fromWire(value: String): DiscoverCategory =
            entries.firstOrNull { it.name == value.uppercase() } ?: ALL
    }
}

/** 精选仓库种子（产品配置，非抓取事实）：owner/name + 分类 + 中文推荐语。 */
data class FeaturedRepo(
    val owner: String,
    val name: String,
    val category: DiscoverCategory,
    val tagline: String,
    val topics: List<String> = emptyList(),
) {
    val fullName: String get() = "$owner/$name"
}

/** Release 资产（可下载产物）。 */
data class ReleaseAsset(
    val id: Long,
    val name: String,
    val size: Long,
    val downloadUrl: String,
    val contentType: String,
    val downloadCount: Long = 0L,
)

/** 一次 Release 发布的信息。 */
@Suppress("LongParameterList")
data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val publishedAt: String?,
    val htmlUrl: String,
    val body: String?,
    val isPrerelease: Boolean,
    val isDraft: Boolean,
    val assets: List<ReleaseAsset> = emptyList(),
)

/** 发现列表条目：精选仓库 + 其最新 Release（可能未抓取到）。 */
data class DiscoverItem(
    val featured: FeaturedRepo,
    val latestRelease: ReleaseInfo?,
    val fetchedAt: String?,
)

/** 镜像地址解析结果：展示原始直连地址与（可选）加速地址。 */
data class MirrorDownloadLink(
    val originalUrl: String,
    val acceleratedUrl: String?,
    val mirrorName: String?,
    val isAccelerated: Boolean,
)
