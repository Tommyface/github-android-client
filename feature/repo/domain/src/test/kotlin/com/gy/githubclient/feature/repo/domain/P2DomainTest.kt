/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain

import com.gy.githubclient.feature.repo.domain.model.RepoTree
import com.gy.githubclient.feature.repo.domain.model.TreeNode
import com.gy.githubclient.feature.repo.domain.model.languageShares
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** P2 领域逻辑（v0.16.0）：语言占比 + 文件树统计。 */
class P2DomainTest {
    @Test
    fun languageSharesSortByBytesAndComputePercent() {
        val shares = languageShares(mapOf("Kotlin" to 800L, "Shell" to 200L))

        assertEquals(2, shares.size)
        assertEquals("Kotlin", shares.first().language)
        assertEquals(80.0, shares.first().percent, 0.01)
        assertEquals(20.0, shares.last().percent, 0.01)
    }

    @Test
    fun languageSharesIgnoreZeroAndNegative() {
        val shares = languageShares(mapOf("Kotlin" to 100L, "Empty" to 0L, "Bad" to -5L))

        assertEquals(1, shares.size)
        assertEquals("Kotlin", shares.first().language)
        assertEquals(100.0, shares.first().percent, 0.01)
    }

    @Test
    fun languageSharesEmptyForNoData() {
        assertTrue(languageShares(emptyMap()).isEmpty())
        assertTrue(languageShares(mapOf("A" to 0L)).isEmpty())
    }

    @Test
    fun languageSharesPercentRoundedToOneDecimal() {
        val shares = languageShares(mapOf("A" to 1L, "B" to 1L, "C" to 1L))

        assertEquals(3, shares.size)
        assertTrue(shares.all { it.percent == 33.3 })
    }

    @Test
    fun treeCountsFilesAndDirs() {
        val tree =
            RepoTree(
                nodes =
                    listOf(
                        TreeNode(path = "src", type = "tree", size = 0, sha = "a"),
                        TreeNode(path = "src/Main.kt", type = "blob", size = 100, sha = "b"),
                        TreeNode(path = "README.md", type = "blob", size = 50, sha = "c"),
                    ),
            )

        assertEquals(2, tree.fileCount)
        assertEquals(1, tree.dirCount)
        assertTrue(tree.nodes[0].isDirectory)
        assertTrue(tree.nodes[1].isFile)
    }

    @Test
    fun emptyTreeHasZeroCounts() {
        val tree = RepoTree(emptyList(), isTruncated = true)
        assertEquals(0, tree.fileCount)
        assertEquals(0, tree.dirCount)
        assertTrue(tree.isTruncated)
    }
}
