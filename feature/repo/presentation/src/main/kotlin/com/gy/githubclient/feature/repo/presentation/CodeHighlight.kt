/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

/** 词法 token 类别（v0.11.0 N8）：零第三方依赖的轻量语法高亮。 */
enum class TokenKind {
    KEYWORD,
    STRING,
    COMMENT,
    NUMBER,
    FUNCTION,
    PLAIN,
}

/** 单个词法单元：[text] 原文 + [kind] 着色类别。 */
data class CodeToken(
    val text: String,
    val kind: TokenKind,
)

/**
 * 通用词法扫描（纯函数，可单测）：覆盖行/块注释、字符串（含三引号）、数字、关键字、
 * 函数名（标识符后紧跟左括号）。不做完整语法树，未知语言退化为通用关键字高亮。
 */
fun tokenizeCode(
    code: String,
    languageHint: String? = null,
): List<CodeToken> {
    val keywords = keywordsFor(languageHint)
    val tokens = mutableListOf<CodeToken>()
    var i = 0
    while (i < code.length) {
        val token = nextToken(code, i, keywords)
        tokens += token
        i += token.text.length
    }
    return tokens
}

/** 从 [start] 起识别一个 token；分派到各扫描小函数以保持低圈复杂度。 */
private fun nextToken(
    code: String,
    start: Int,
    keywords: Set<String>,
): CodeToken {
    val c = code[start]
    return when {
        isLineComment(code, start) -> scanToEnd(code, start, '\n', TokenKind.COMMENT)
        isBlockComment(code, start) -> scanPair(code, start, "*/", 2, TokenKind.COMMENT)
        isTripleQuote(code, start) -> scanPair(code, start, "\"\"\"", 3, TokenKind.STRING)
        c == '"' || c == '\'' -> scanQuoted(code, start, c)
        c.isDigit() -> scanNumber(code, start)
        c.isLetter() || c == '_' -> scanWord(code, start, keywords)
        else -> CodeToken(c.toString(), TokenKind.PLAIN)
    }
}

private fun isLineComment(
    code: String,
    i: Int,
): Boolean = code[i] == '/' && i + 1 < code.length && code[i + 1] == '/'

private fun isBlockComment(
    code: String,
    i: Int,
): Boolean = code[i] == '/' && i + 1 < code.length && code[i + 1] == '*'

private fun isTripleQuote(
    code: String,
    i: Int,
): Boolean = i + 2 < code.length && code[i] == '"' && code[i + 1] == '"' && code[i + 2] == '"'

/** 行注释：从左斜杠扫到换行（不含），未换行则到末尾。 */
private fun scanToEnd(
    code: String,
    start: Int,
    terminator: Char,
    kind: TokenKind,
): CodeToken {
    val found = code.indexOf(terminator, start)
    val end = if (found < 0) code.length else found
    return CodeToken(code.substring(start, end), kind)
}

/** 块注释/三引号：从 [start] 扫到闭合串之后，缺失闭合串则到末尾。 */
private fun scanPair(
    code: String,
    start: Int,
    closing: String,
    closingLength: Int,
    kind: TokenKind,
): CodeToken {
    val found = code.indexOf(closing, start + closingLength)
    val end = if (found < 0) code.length else found + closingLength
    return CodeToken(code.substring(start, end), kind)
}

/** 单/双引号字符串，支持反斜杠转义；未闭合时到末尾。 */
private fun scanQuoted(
    code: String,
    start: Int,
    quote: Char,
): CodeToken {
    var j = start + 1
    var closed = false
    while (j < code.length && !closed) {
        when (code[j]) {
            '\\' -> j += 2
            quote -> {
                j++
                closed = true
            }

            else -> j++
        }
    }
    return CodeToken(code.substring(start, j.coerceAtMost(code.length)), TokenKind.STRING)
}

/** 数字字面量：数字、小数点和十六进制字符。 */
private fun scanNumber(
    code: String,
    start: Int,
): CodeToken {
    var j = start + 1
    while (j < code.length && isNumberChar(code[j])) {
        j++
    }
    return CodeToken(code.substring(start, j), TokenKind.NUMBER)
}

private fun isNumberChar(c: Char): Boolean =
    c.isDigit() ||
        c == '.' ||
        c == 'x' ||
        c == 'X' ||
        c in 'a'..'f' ||
        c in 'A'..'F'

/** 标识符：判定关键字、函数名（后随左括号）或普通文本。 */
private fun scanWord(
    code: String,
    start: Int,
    keywords: Set<String>,
): CodeToken {
    var j = start + 1
    while (j < code.length && (code[j].isLetterOrDigit() || code[j] == '_')) {
        j++
    }
    val word = code.substring(start, j)
    val kind =
        when {
            word in keywords -> TokenKind.KEYWORD
            j < code.length && code[j] == '(' -> TokenKind.FUNCTION
            else -> TokenKind.PLAIN
        }
    return CodeToken(word, kind)
}

/** 按语言提示补充关键字集合；kotlin/java 共用主干，json/xml 走通用着色。 */
private fun keywordsFor(languageHint: String?): Set<String> {
    val common =
        setOf(
            "val",
            "var",
            "fun",
            "class",
            "object",
            "interface",
            "data",
            "package",
            "import",
            "if",
            "else",
            "when",
            "for",
            "while",
            "do",
            "return",
            "break",
            "continue",
            "true",
            "false",
            "null",
            "this",
            "super",
            "new",
            "throw",
            "try",
            "catch",
            "finally",
        )
    return when (languageHint?.lowercase().orEmpty()) {
        "json" -> setOf("true", "false", "null")
        "xml" -> common
        else ->
            common +
                setOf(
                    "public",
                    "private",
                    "protected",
                    "static",
                    "final",
                    "override",
                    "suspend",
                    "inline",
                    "int",
                    "long",
                    "string",
                    "void",
                    "enum",
                    "sealed",
                )
    }
}
