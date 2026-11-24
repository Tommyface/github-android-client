/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.domain

import com.gy.githubclient.feature.discover.domain.model.PlatformInference
import com.gy.githubclient.feature.discover.domain.model.RankQueryFactory
import kotlinx.datetime.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.11.0 N6/N7 回归：榜单查询 qualifier、日期下界与平台启发式推断。 */
class RankAndPlatformTest {
    @Test
    fun sinceDateIsIsoDateOnly() {
        // 2026-11-24 往前 30 天 = 2026-10-25（当日 UTC）。
        val date = RankQueryFactory.sinceDateAt(Instant.parse("2026-11-24T12:34:56Z"), 30)
        assertEquals("2026-10-25", date)
        assertEquals(10, date.length)
    }

    @Test
    fun buildTermAddsTrendingAndLanguageQualifiers() {
        val since = "2026-10-25"
        val trendingAll = RankQueryFactory.buildTerm("全部", since, trending = true)
        assertTrue(trendingAll.contains("created:>$since"))
        assertTrue(trendingAll.contains("stars:>100"))
        assertTrue(!trendingAll.contains("language:"))

        val hotKotlin = RankQueryFactory.buildTerm("Kotlin", since, trending = false)
        assertTrue(!hotKotlin.contains("created:"))
        assertTrue(hotKotlin.contains("stars:>100"))
        assertTrue(hotKotlin.contains("language:Kotlin"))

        val blankLang = RankQueryFactory.buildTerm("  ", since, trending = false)
        assertTrue(!blankLang.contains("language:"))
    }

    @Test
    fun languageListStartsWithAll() {
        assertEquals("全部", RankQueryFactory.LANGUAGES.first())
        assertTrue(RankQueryFactory.LANGUAGES.contains("Kotlin"))
    }

    @Test
    fun platformInferenceFromTopicsAndAssets() {
        assertEquals(listOf("Android"), PlatformInference.infer(listOf("android", "kotlin"), emptyList()))
        assertEquals(listOf("Windows", "macOS"), PlatformInference.infer(emptyList(), listOf("app.exe", "app.dmg")))
        assertTrue(PlatformInference.infer(emptyList(), listOf("AppImage-x86_64")).contains("Linux"))
        assertEquals(emptyList<String>(), PlatformInference.infer(listOf("cli", "tool"), listOf("jar")))
    }

    @Test
    fun platformInferenceIsCaseInsensitiveAndDeduplicated() {
        val result = PlatformInference.infer(listOf("ANDROID"), listOf("APP.APK", "notes.txt"))
        assertEquals(1, result.count { it == "Android" })
    }
}
