/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.domain.model

import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * 榜单查询构造（v0.11.0 N6）：语言 qualifier + 趋势 created:>date。
 * 口径均为 Search API 官方 qualifier，结果在 UI 明示「非官方近似榜」。
 */
object RankQueryFactory {
    /** 语言筛选项；第一项为「不筛选」。 */
    val LANGUAGES = listOf("全部", "Kotlin", "Java", "Swift", "Dart", "Python", "JavaScript", "Go", "Rust", "C++")

    /** 近 N 天新建仓库的日期下界（ISO yyyy-MM-dd）；无参签名不暴露 Instant 类型。 */
    fun sinceDate(): String = sinceDateAt(Clock.System.now(), TRENDING_WINDOW_DAYS)

    /** 可注入时刻的纯函数版本，供单测固定时钟。 */
    internal fun sinceDateAt(
        now: Instant,
        days: Int = TRENDING_WINDOW_DAYS,
    ): String = Instant.fromEpochSeconds(now.epochSeconds - days * SECONDS_PER_DAY).toString().take(DATE_LENGTH)

    /**
     * 组装 Search qualifier：
     * - 趋势 tab 加 created:>date（近 30 天新建）；
     * - 恒加 stars:>100 过滤噪音；
     * - 语言非「全部」时加 language:xxx。
     */
    fun buildTerm(
        language: String,
        sinceDate: String,
        trending: Boolean,
    ): String {
        val parts = mutableListOf<String>()
        if (trending) parts += "created:>$sinceDate"
        parts += "stars:>$STAR_THRESHOLD"
        if (language.isNotBlank() && language != LANGUAGES.first()) parts += "language:$language"
        return parts.joinToString(" ")
    }

    private const val TRENDING_WINDOW_DAYS = 30
    private const val STAR_THRESHOLD = 100
    private const val DATE_LENGTH = 10
    private const val SECONDS_PER_DAY = 86_400L
}

/**
 * 平台标签启发式推断（v0.11.0 N7）：从 topics + Release 资产文件名推断目标平台。
 * 结果仅作展示辅助，UI 必须标注「推断」，不声称官方数据。
 */
object PlatformInference {
    private val RULES =
        listOf(
            "Android" to listOf("android", ".apk"),
            "Windows" to listOf("windows", ".exe", ".msi"),
            "macOS" to listOf("macos", ".dmg", ".pkg"),
            "Linux" to listOf("linux", "appimage", ".deb", ".rpm"),
            "iOS" to listOf("ios", ".ipa"),
        )

    /** 命中平台按 RULES 顺序返回（稳定），无命中返回空列表。 */
    fun infer(
        topics: List<String>,
        assetNames: List<String>,
    ): List<String> {
        val signals = (topics + assetNames).map { it.lowercase() }
        return RULES
            .filter { (_, keywords) -> keywords.any { kw -> signals.any { it.contains(kw) } } }
            .map { (platform, _) -> platform }
    }
}
