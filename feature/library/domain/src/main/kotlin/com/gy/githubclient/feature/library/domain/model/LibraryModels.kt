/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.library.domain.model

/** 用户追踪的一个应用：仓库坐标 + 已安装版本 + ABI 偏好。 */
@Suppress("LongParameterList")
data class TrackedApp(
    val owner: String,
    val name: String,
    val displayName: String,
    /** 已安装版本号（如 v0.8.31）；为空表示仅追踪、未安装。 */
    val installedVersion: String = "",
    /** ABI 偏好（如 arm64-v8a）；为空表示接受任意 APK 资产。 */
    val abi: String = "",
    val addedAt: String = "",
) {
    val fullName: String get() = "$owner/$name"
}

/** ABI 候选，用于「变体」下拉选择。 */
enum class AbiVariant(
    val label: String,
    val token: String,
) {
    ANY("通用", ""),
    ARM64("arm64-v8a", "arm64-v8a"),
    ARM32("armeabi-v7a", "armeabi-v7a"),
    X86_64("x86_64", "x86_64"),
    X86("x86", "x86"),
    ;

    companion object {
        fun fromToken(value: String): AbiVariant = entries.firstOrNull { it.token.equals(value, ignoreCase = true) } ?: ANY
    }
}

/** 一个追踪项的更新检查结果。 */
@Suppress("LongParameterList")
data class LibraryEntry(
    val app: TrackedApp,
    /** 最新版本 tag（拉取成功时）。 */
    val latestVersion: String? = null,
    /** 发布日期（ISO 字符串，展示用）。 */
    val publishedAt: String? = null,
    /** 与已安装版本相比是否有更新。 */
    val hasUpdate: Boolean = false,
    /** 选中的可下载 APK 资产（按 ABI 偏好筛选）。 */
    val download: DownloadCandidate? = null,
    /** 检查失败原因（null 表示成功）。 */
    val error: String? = null,
)

/** 可下载候选：资产名 + URL + 大小 + 官方摘要（供下载引擎双闸校验）。 */
@Suppress("LongParameterList")
data class DownloadCandidate(
    val assetName: String,
    val url: String,
    val size: Long,
    val sha256: String?,
)

/**
 * 判断远端 tag 是否新于已安装版本（纯函数，可单测）。
 * 优先语义版本比较；任一侧无法解析时退化为字符串不等即视为有更新（宁提示，不漏报）。
 */
fun isUpdateAvailable(
    latest: String,
    installed: String,
): Boolean {
    if (installed.isBlank()) return true
    if (latest.equals(installed, ignoreCase = true)) return false
    val l = parseVersion(latest)
    val i = parseVersion(installed)
    return if (l != null && i != null) {
        compareTriple(l, i) > 0
    } else {
        true
    }
}

private fun parseVersion(text: String): Triple<Int, Int, Int>? {
    val cleaned = text.trim().removePrefix("v").removePrefix("V")
    val core = cleaned.substringBefore("-").substringBefore("+")
    val parts = core.split(".")
    if (parts.isEmpty() || parts.size > 3) return null
    val major = parts[0].toIntOrNull() ?: return null
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0
    return Triple(major, minor, patch)
}

private fun compareTriple(
    a: Triple<Int, Int, Int>,
    b: Triple<Int, Int, Int>,
): Int {
    for ((x, y) in listOf(a.first to b.first, a.second to b.second, a.third to b.third)) {
        if (x != y) return x.compareTo(y)
    }
    return 0
}
