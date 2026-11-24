/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.11.0 N8 回归：零依赖词法高亮的关键 token 分类。 */
class CodeHighlightTest {
    @Test
    fun classifiesKeywordsStringsCommentsNumbers() {
        val code =
            """
            // 一行注释
            val name = "hello"
            fun add(x: Int): Int {
                /* 块注释 */ return 42
            }
            """.trimIndent()
        val tokens = tokenizeCode(code, "kotlin")

        assertTrue(tokens.any { it.kind == TokenKind.KEYWORD && it.text == "val" })
        assertTrue(tokens.any { it.kind == TokenKind.KEYWORD && it.text == "fun" })
        assertTrue(tokens.any { it.kind == TokenKind.STRING && it.text == "\"hello\"" })
        assertTrue(tokens.any { it.kind == TokenKind.COMMENT && it.text.startsWith("//") })
        assertTrue(tokens.any { it.kind == TokenKind.COMMENT && it.text.startsWith("/*") })
        assertTrue(tokens.any { it.kind == TokenKind.NUMBER && it.text == "42" })
        assertTrue(tokens.any { it.kind == TokenKind.FUNCTION && it.text == "add" })
        // token 拼接后须完整还原原文（除空白逐字符 token 也保留）。
        assertEquals(code, tokens.joinToString("") { it.text })
    }

    @Test
    fun handlesTripleQuotedStringAndEscapes() {
        val triple = "\"\"\"raw text\"\"\""
        val escaped = "\"x\\\"y\""
        val code = "val s = $triple\nval t = $escaped"
        val tokens = tokenizeCode(code, "kotlin")
        assertTrue(tokens.any { it.kind == TokenKind.STRING && it.text == triple })
        assertTrue(tokens.any { it.kind == TokenKind.STRING && it.text == escaped })
        assertEquals(code, tokens.joinToString("") { it.text })
    }

    @Test
    fun unknownLanguageDegradesToCommonHighlighting() {
        val tokens = tokenizeCode("val x = 1", "weirdlang")
        assertTrue(tokens.any { it.kind == TokenKind.KEYWORD && it.text == "val" })
        assertTrue(tokens.any { it.kind == TokenKind.NUMBER && it.text == "1" })
    }

    @Test
    fun emptyAndPlainInput() {
        assertTrue(tokenizeCode("").isEmpty())
        val tokens = tokenizeCode("abc def", null)
        assertTrue(tokens.all { it.kind == TokenKind.PLAIN })
        assertEquals("abc def", tokens.joinToString("") { it.text })
    }
}
