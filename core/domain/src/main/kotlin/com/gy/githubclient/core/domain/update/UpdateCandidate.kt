/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.update

/**
 * 一次更新检查的**远端候选**（与通道无关的统一表示）。
 *
 * 两个通道（GitHub Release API / version.json）都归一化成这个结构，
 * 再由 [UpdateChannelMerger] 合并，避免上层到处判断"这次是哪个通道来的"。
 */
data class UpdateCandidate(
    /** 远端版本号（形如 0.19.0）。 */
    val version: String,
    /** 远端 versionCode（Release body 的 `versionCode: N` 约定或 version.json 字段）。 */
    val versionCode: Int? = null,
    /** 远端产物指纹（SHA-256 小写十六进制）；null 表示摘要缺失。 */
    val fingerprint: String? = null,
    /** 产物下载地址。 */
    val downloadUrl: String? = null,
    /** 产物字节数（-1 未知）。 */
    val assetSize: Long = -1L,
    /** 更新日志条目。 */
    val notes: List<String> = emptyList(),
    /** 来源通道标识（仅用于诊断与日志）。 */
    val channel: String = CHANNEL_RELEASE,
) {
    companion object {
        const val CHANNEL_RELEASE = "release"
        const val CHANNEL_VERSION_JSON = "version.json"
    }
}

/**
 * 双通道合并规则（纯函数）：
 *
 * 1. 主通道（Release API）可用 → 以主通道为准；
 * 2. 主通道缺失 → 回退到 version.json 通道；
 * 3. 主通道存在但**缺指纹**、回退通道有指纹 → 借回退通道的指纹补齐
 *    （指纹是"指纹双信号"的必需项，缺了就失去一半判别力）；
 * 4. 两通道都不可用 → null（静默）。
 */
object UpdateChannelMerger {
    fun merge(
        primary: UpdateCandidate?,
        fallback: UpdateCandidate?,
    ): UpdateCandidate? {
        if (primary == null && fallback == null) return null
        if (primary == null) return fallback
        if (fallback == null) return primary
        if (primary.fingerprint.isNullOrBlank() && !fallback.fingerprint.isNullOrBlank()) {
            return primary.copy(fingerprint = fallback.fingerprint)
        }
        return primary
    }
}

/**
 * 更新相关的本地状态账本（端口）。
 *
 * 实现落在 app 层（SharedPreferences），领域层只依赖接口，便于纯逻辑单测。
 */
interface UpdateSkipLedger {
    /** 全部已跳过的（版本号 + 指纹）键集合。 */
    fun skippedKeys(): Set<SkipKey>

    /** 该（版本号 + 指纹）是否已被用户跳过。 */
    fun isSkipped(key: SkipKey): Boolean = key in skippedKeys()

    /** 记录一次跳过。 */
    fun recordSkip(key: SkipKey)

    /** 上次点击安装的时间戳；null 表示从未点击。 */
    fun lastInstallRequestedAt(): Long?

    /** 记录一次安装点击（用于安装 TTL，防装失败死循环）。 */
    fun recordInstallRequested(at: Long)

    /** 上次检查时间戳（用于检查节流）。 */
    fun lastCheckAt(): Long

    /** 记录一次检查时间。 */
    fun recordCheckAt(at: Long)

    /** 检查节流窗口（毫秒）。 */
    fun throttleMillis(): Long
}

/**
 * 远端候选来源（端口）：把两个通道合并成一个候选。
 */
interface UpdateCandidateSource {
    /** 取回远端候选；两通道均不可用时返回 null（静默）。 */
    suspend fun fetch(): UpdateCandidate?
}

/**
 * 领域层的默认节流窗口：30 分钟（沿用壳工程 UpdatePreferences 的取值）。
 */
const val DEFAULT_UPDATE_THROTTLE_MILLIS: Long = 30 * 60 * 1000L

/**
 * 是否处于检查节流窗口内（纯函数）。
 */
fun isWithinThrottle(
    lastCheckAt: Long,
    now: Long,
    throttleMillis: Long,
): Boolean = lastCheckAt > 0 && now - lastCheckAt < throttleMillis
