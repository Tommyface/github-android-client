/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.data.catalog

import com.gy.githubclient.feature.discover.domain.model.DiscoverCategory
import com.gy.githubclient.feature.discover.domain.model.FeaturedRepo

/**
 * 精选仓库种子清单（产品配置，非抓取事实）。
 * 均为真实存在的知名开源项目；Release 数据在运行时通过 GitHub API 实时拉取。
 * 分类与推荐语为产品编辑内容，不涉及版本/下载量等易变事实。
 */
object BundledFeatured {
    val ALL: List<FeaturedRepo> =
        listOf(
            FeaturedRepo(
                "android",
                "nowinandroid",
                DiscoverCategory.ANDROID,
                "Google 官方 Android 现代开发范例 App",
                listOf("android", "kotlin", "compose"),
            ),
            FeaturedRepo("androidx", "compose", DiscoverCategory.ANDROID, "Jetpack Compose 声明式 UI 工具包", listOf("android", "compose", "ui")),
            FeaturedRepo("square", "retrofit", DiscoverCategory.DEVELOPER, "类型安全的 HTTP 客户端", listOf("kotlin", "http", "android")),
            FeaturedRepo("square", "okhttp", DiscoverCategory.DEVELOPER, "高效 HTTP 客户端", listOf("kotlin", "networking")),
            FeaturedRepo("JetBrains", "kotlin", DiscoverCategory.DEVELOPER, "Kotlin 编程语言", listOf("kotlin", "language")),
            FeaturedRepo(
                "home-assistant",
                "android",
                DiscoverCategory.UTILITY,
                "Home Assistant 官方 Android 客户端",
                listOf("smart-home", "android"),
            ),
            FeaturedRepo("VLC", "android", DiscoverCategory.MEDIA, "VLC 媒体播放器安卓版", listOf("video", "player")),
            FeaturedRepo("kiwix", "kiwix-android", DiscoverCategory.UTILITY, "离线维基百科阅读器", listOf("offline", "knowledge")),
            FeaturedRepo("olibre", "aweather", DiscoverCategory.UTILITY, "开源天气应用", listOf("weather", "android")),
            FeaturedRepo("fdroid", "fdroidclient", DiscoverCategory.UTILITY, "F-Droid 开源应用商店客户端", listOf("foss", "store")),
        )
}
