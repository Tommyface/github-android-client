/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.6.0 M6 回归：Markdown 块解析、图片镜像改写与 HTML 降级。 */
class MarkdownLiteTest {
    @Test
    fun parsesHeadingsListsCodeAndImages() {
        val md =
            """
            # 标题一
            ## 标题二

            普通段落，含 **粗体** 与 `code`。

            - 列表项 A
            * 列表项 B
            1. 有序项 C

            ![logo](https://raw.githubusercontent.com/Tommyface/github-android-client/main/docs/logo.png)

            ```kotlin
            val x = 1
            ```

            ---
            """.trimIndent()
        val blocks = parseMarkdownBlocks(md)
        assertTrue(blocks[0] is MdBlock.Heading && (blocks[0] as MdBlock.Heading).level == 1)
        assertTrue(blocks[1] is MdBlock.Heading && (blocks[1] as MdBlock.Heading).level == 2)
        assertTrue(blocks[2] is MdBlock.Paragraph)
        val listItems = blocks.filterIsInstance<MdBlock.ListItem>()
        assertEquals(3, listItems.size)
        val image = blocks.filterIsInstance<MdBlock.Image>().single()
        assertTrue(image.url.contains("raw.githubusercontent.com"))
        val code = blocks.filterIsInstance<MdBlock.CodeBlock>().single()
        assertEquals("val x = 1", code.code)
        assertTrue(blocks.last() is MdBlock.Rule)
    }

    @Test
    fun rewritesRawAndRelativeImageUrls() {
        assertEquals(
            "https://fastly.jsdelivr.net/gh/Tommyface/github-android-client@main/docs/logo.png",
            rewriteReadmeImageUrl(
                "https://raw.githubusercontent.com/Tommyface/github-android-client/main/docs/logo.png",
                "Tommyface",
                "github-android-client",
                "main",
            ),
        )
        assertEquals(
            "https://fastly.jsdelivr.net/gh/Tommyface/github-android-client@dev/assets/a.png",
            rewriteReadmeImageUrl(
                "https://github.com/Tommyface/github-android-client/raw/dev/assets/a.png",
                "Tommyface",
                "github-android-client",
                "main",
            ),
        )
        // 相对路径锚定默认分支
        assertEquals(
            "https://fastly.jsdelivr.net/gh/Tommyface/github-android-client@main/img/i.png",
            rewriteReadmeImageUrl("img/i.png", "Tommyface", "github-android-client", "main"),
        )
        // 他仓库外链原样保留
        assertEquals(
            "https://raw.githubusercontent.com/octocat/Hello-World/master/README.png",
            rewriteReadmeImageUrl(
                "https://raw.githubusercontent.com/octocat/Hello-World/master/README.png",
                "Tommyface",
                "github-android-client",
                "main",
            ),
        )
    }

    @Test
    fun stripsHtmlTagsAndEntities() {
        assertEquals("居中文字 换行", stripHtmlTags("<div align=\"center\">居中文字</div><br>换行"))
        assertEquals("纯文本", stripHtmlTags("&nbsp;纯文本"))
    }

    @Test
    fun unterminatedCodeFenceIsFlushedAtEnd() {
        val blocks = parseMarkdownBlocks("```\nabc")
        assertTrue(blocks.single() is MdBlock.CodeBlock)
    }
}
