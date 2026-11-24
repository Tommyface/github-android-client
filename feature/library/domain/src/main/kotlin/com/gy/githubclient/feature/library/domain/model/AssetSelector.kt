/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.library.domain.model

import com.gy.githubclient.feature.discover.domain.model.ReleaseAsset

/** ABI 关键字 → 归一化 token，用于资产名匹配（顺序：长 token 优先，避免 x86 命中 x86_64）。 */
private val ABI_TOKENS =
    listOf(
        "arm64-v8a",
        "armeabi-v7a",
        "arm64",
        "x86_64",
        "x86",
        "universal",
    )

/**
 * 从 Release 资产中选出可安装的 APK（纯函数，可单测）：
 * 1. 只保留 .apk 资产；
 * 2. 若指定 ABI，优先选文件名含该 token 的资产；
 * 3. 未命中时回退到第一个 APK（保证有下载候选）。
 */
fun selectApkAsset(
    assets: List<ReleaseAsset>,
    abi: String,
): ReleaseAsset? {
    val apks = assets.filter { it.name.endsWith(".apk", ignoreCase = true) }
    if (apks.isEmpty()) return null
    if (abi.isNotBlank()) {
        val normalized = abi.lowercase().replace("_", "-")
        apks
            .firstOrNull { asset -> normalized in asset.name.lowercase().replace("_", "-") }
            ?.let { return it }
    }
    return apks.first()
}

/**
 * 从资产名推断平台标签（启发式，纯函数）：文件名含 apk 视为 Android；
 * 其余按扩展名/关键字推断，无法判断返回 null（UI 不展示误导性标签）。
 */
fun inferPlatform(assetName: String): String? =
    when {
        assetName.endsWith(".apk", ignoreCase = true) -> "Android"
        assetName.contains("windows", ignoreCase = true) || assetName.contains(".msi", ignoreCase = true) -> "Windows"
        assetName.contains("macos", ignoreCase = true) || assetName.contains(".dmg", ignoreCase = true) -> "macOS"
        assetName.contains("linux", ignoreCase = true) || assetName.contains("appimage", ignoreCase = true) -> "Linux"
        else -> null
    }

/** 归一化 ABI token：把展示名映射为资产名常见写法（arm64-v8a 兼容 arm64）。 */
fun normalizeAbiToken(abi: String): String =
    ABI_TOKENS.firstOrNull { abi.contains(it, ignoreCase = true) }.orEmpty()
