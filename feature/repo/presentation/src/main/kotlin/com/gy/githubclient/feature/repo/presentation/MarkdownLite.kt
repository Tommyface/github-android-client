/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

/**
 * 轻量 Markdown 块级解析（v0.6.0 M6）：
 * 只覆盖 GitHub README 的高频结构（标题/段落/列表/围栏代码块/图片/分隔线），
 * 表格与嵌套列表等复杂结构按普通段落降级展示——宁降级，不丢内容。
 */
sealed class MdBlock {
    data class Heading(
        val level: Int,
        val text: String,
    ) : MdBlock()

    data class Paragraph(
        val text: String,
    ) : MdBlock()

    data class ListItem(
        val text: String,
    ) : MdBlock()

    data class CodeBlock(
        val code: String,
    ) : MdBlock()

    data class Image(
        val url: String,
        val alt: String,
    ) : MdBlock()

    data object Rule : MdBlock()
}

/** 行类型判定所需的正则集合。 */
private val HEADING_REGEX = Regex("^(#{1,6})\\s+(.*)$")
private val LIST_ITEM_REGEX = Regex("^\\s*(?:[-*+]|\\d+[.)])\\s+(.*)$")
private val IMAGE_ONLY_REGEX = Regex("^!\\[([^\\]]*)]\\(([^)\\s]+)\\)$")
private val RULE_REGEX = Regex("^\\s*([-*_])\\s*\\1\\s*\\1[-*_\\s]*$")

/**
 * 把 README 原文解析为块序列（纯函数，可单测）。
 * 连续非列表行合并为同一段落；行内格式由渲染层 AnnotatedString 处理。
 */
fun parseMarkdownBlocks(markdown: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val paragraph = StringBuilder()
    var codeLines: MutableList<String>? = null

    for (rawLine in markdown.lineSequence()) {
        val line = rawLine.trimEnd()
        when {
            line.isBlank() -> flushParagraph(paragraph, blocks)

            line.startsWith("```") -> {
                codeLines?.let { closed -> blocks += MdBlock.CodeBlock(closed.joinToString("\n")) }
                flushParagraph(paragraph, blocks)
                codeLines = if (codeLines == null) mutableListOf() else null
            }

            codeLines != null -> codeLines += rawLine

            else -> appendPlainLine(line, blocks, paragraph)
        }
    }
    if (codeLines != null) {
        blocks += MdBlock.CodeBlock(codeLines.joinToString("\n"))
    }
    flushParagraph(paragraph, blocks)
    return blocks
}

/** 段落聚合收尾：非空段落整体转 Paragraph 块（含 HTML 降级）。 */
private fun flushParagraph(
    paragraph: StringBuilder,
    blocks: MutableList<MdBlock>,
) {
    val text = paragraph.toString().trim()
    paragraph.clear()
    if (text.isNotEmpty()) blocks += MdBlock.Paragraph(stripHtmlTags(text))
}

/** 非围栏行落点：可识别为块则先收段落再入块，否则并入当前段落。 */
private fun appendPlainLine(
    line: String,
    blocks: MutableList<MdBlock>,
    paragraph: StringBuilder,
) {
    val recognized = recognizeBlockLine(line)
    if (recognized != null) {
        flushParagraph(paragraph, blocks)
        blocks += recognized
        return
    }
    if (paragraph.isNotEmpty()) paragraph.append('\n')
    paragraph.append(line.trim())
}

/** 单行（非围栏内）块级识别：标题/独立图片/列表/分隔线；段落行返回 null。 */
private fun recognizeBlockLine(line: String): MdBlock? {
    HEADING_REGEX.matchEntire(line)?.let {
        return MdBlock.Heading(it.groupValues[1].length, it.groupValues[2])
    }
    IMAGE_ONLY_REGEX.matchEntire(line.trim())?.let {
        return MdBlock.Image(it.groupValues[2], it.groupValues[1])
    }
    LIST_ITEM_REGEX.matchEntire(line)?.let {
        return MdBlock.ListItem(it.groupValues[1])
    }
    if (RULE_REGEX.matchEntire(line) != null) return MdBlock.Rule
    return null
}

/** 剥离 README 里的 HTML 标签（<img>/<br>/<div> 等），避免裸标签污染文本；相邻标签产生的连续空格压缩为单个。 */
internal fun stripHtmlTags(text: String): String =
    text
        .replace(HTML_TAG_REGEX, " ")
        .replace(HTML_ENTITY_REGEX, " ")
        .replace(MULTI_SPACE_REGEX, " ")
        .trim()

private val HTML_TAG_REGEX = Regex("<[^>]+>")
private val HTML_ENTITY_REGEX = Regex("&[a-zA-Z]+;")
private val MULTI_SPACE_REGEX = Regex(" {2,}")

/**
 * README 图片地址镜像改写（纯函数，可单测）：
 * - raw.githubusercontent.com/{owner}/{repo}/{ref}/{path} → fastly.jsdelivr.net/gh/{owner}/{repo}@{ref}/{path}
 * - github.com/{owner}/{repo}/raw/{ref}/{path} → 同上
 * - 其余（绝对外链等）原样返回。
 */
internal fun rewriteReadmeImageUrl(
    url: String,
    owner: String,
    repo: String,
    defaultBranch: String,
): String {
    val jsdelivr = "https://fastly.jsdelivr.net/gh/$owner/$repo"
    val raw = Regex("^https?://raw\\.githubusercontent\\.com/([^/]+)/([^/]+)/([^/]+)/(.+)$").matchEntire(url)
    if (raw != null && raw.groupValues[1] == owner && raw.groupValues[2] == repo) {
        return "$jsdelivr@${raw.groupValues[3]}/${raw.groupValues[4]}"
    }
    val githubRaw = Regex("^https?://github\\.com/([^/]+)/([^/]+)/raw/([^/]+)/(.+)$").matchEntire(url)
    if (githubRaw != null && githubRaw.groupValues[1] == owner && githubRaw.groupValues[2] == repo) {
        return "$jsdelivr@${githubRaw.groupValues[3]}/${githubRaw.groupValues[4]}"
    }
    // 相对路径：锚定到仓库默认分支的 raw 地址（渲染层可直接加载）。
    if (!url.startsWith("http")) {
        val ref = defaultBranch.ifBlank { "main" }
        val path = url.removePrefix("/")
        return "https://fastly.jsdelivr.net/gh/$owner/$repo@$ref/$path"
    }
    return url
}
