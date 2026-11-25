/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain

import com.gy.githubclient.feature.repo.domain.translation.TranslationEligibility
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * README 翻译准入规则测试（v0.16.0）：
 * 目标是「宁可不翻，也不翻错」——命令、代码、URL、纯中文、版本号必须全部拦截。
 */
class TranslationEligibilityTest {
    @Test
    fun englishProseIsEligible() {
        assertTrue(
            TranslationEligibility.shouldTranslate(
                "A fast, local-first code editor for Android built with Jetpack Compose.",
            ),
        )
    }

    @Test
    fun headingCanBeSingleWordButBodyCannot() {
        assertTrue(TranslationEligibility.shouldTranslate("Installation", isHeading = true))
        assertFalse(TranslationEligibility.shouldTranslate("Installation"))
    }

    @Test
    fun shellAndPackageCommandsAreSkipped() {
        assertFalse(TranslationEligibility.shouldTranslate("npm install github-client"))
        assertFalse(TranslationEligibility.shouldTranslate("$ ./gradlew assembleDebug"))
        assertFalse(TranslationEligibility.shouldTranslate("git clone https://github.com/x/y.git"))
        assertFalse(TranslationEligibility.shouldTranslate("> pip install -r requirements.txt"))
    }

    @Test
    fun urlsAndLinkOnlyLinesAreSkipped() {
        assertFalse(TranslationEligibility.shouldTranslate("https://github.com/Tommyface/github-android-client"))
        assertFalse(TranslationEligibility.shouldTranslate("[docs](https://example.com/docs)"))
        assertFalse(TranslationEligibility.shouldTranslate("www.example.com"))
    }

    @Test
    fun proseContainingLinkRemainsEligible() {
        assertTrue(
            TranslationEligibility.shouldTranslate(
                "See the [documentation](https://example.com/docs) for setup details.",
            ),
        )
    }

    @Test
    fun chineseTextIsSkipped() {
        assertFalse(TranslationEligibility.shouldTranslate("这是一个纯中文的项目说明，无需再次翻译。"))
    }

    @Test
    fun versionsAndIdentifiersAreSkipped() {
        assertFalse(TranslationEligibility.shouldTranslate("v1.2.3"))
        assertFalse(TranslationEligibility.shouldTranslate("com.gy.githubclient"))
        assertFalse(TranslationEligibility.shouldTranslate("README"))
    }

    @Test
    fun codeLikeTextIsSkipped() {
        assertFalse(TranslationEligibility.shouldTranslate("const x = 42;"))
        assertFalse(TranslationEligibility.shouldTranslate("fun main() { println(\"hi\") }"))
    }

    @Test
    fun emptyAndOverlongTextAreSkipped() {
        assertFalse(TranslationEligibility.shouldTranslate("   "))
        assertFalse(TranslationEligibility.shouldTranslate("word ".repeat(1_200)))
    }

    @Test
    fun badgeAndImageLinesAreSkipped() {
        assertFalse(
            TranslationEligibility.shouldTranslate(
                "![Build](https://img.shields.io/github/actions/workflow/status/x/y/ci.yml)",
            ),
        )
    }

    @Test
    fun mixedChineseWithEnglishSentenceIsSkipped() {
        // 中文占比已高：再翻只会破坏原文。
        assertFalse(
            TranslationEligibility.shouldTranslate(
                "这是一个基于 Jetpack Compose 的 GitHub 客户端，支持搜索、浏览与下载。",
            ),
        )
    }
}
