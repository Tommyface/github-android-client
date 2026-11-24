/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.domain

import com.gy.githubclient.feature.profile.domain.model.GistFile
import com.gy.githubclient.feature.profile.domain.model.GistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Gist 领域视图逻辑（v0.15.0 A6）。 */
class OrgGistDomainTest {
    private fun gistWith(vararg files: GistFile) =
        GistItem(
            id = "abc123",
            description = "演示",
            htmlUrl = "https://gist.github.com/abc123",
            isPublic = true,
            ownerLogin = "octocat",
            createdAt = "2026-01-01T00:00:00Z",
            updatedAt = null,
            files = files.toList(),
        )

    @Test
    fun primaryFilePrefersFirstWithContent() {
        val noContent = GistFile(filename = "empty.txt", language = null, type = null, size = 0, content = null, rawUrl = null)
        val withContent = GistFile(filename = "main.kt", language = "Kotlin", type = null, size = 10, content = "fun main()", rawUrl = null)
        val gist = gistWith(noContent, withContent)

        assertEquals("main.kt", gist.primaryFile?.filename)
    }

    @Test
    fun primaryFileFallsBackToFirstWhenNoContent() {
        val first = GistFile(filename = "a.txt", language = "Text", type = null, size = 0, content = null, rawUrl = null)
        val gist = gistWith(first)

        assertEquals("a.txt", gist.primaryFile?.filename)
        assertFalse(first.hasContent)
    }

    @Test
    fun primaryLanguageComesFromFirstFile() {
        val first = GistFile(filename = "a.kt", language = "Kotlin", type = null, size = 0, content = "x", rawUrl = null)
        val second = GistFile(filename = "b.py", language = "Python", type = null, size = 0, content = "y", rawUrl = null)

        assertEquals("Kotlin", gistWith(first, second).primaryLanguage)
    }

    @Test
    fun emptyFilesYieldNullPrimary() {
        val gist = gistWith()

        assertNull(gist.primaryFile)
        assertNull(gist.primaryLanguage)
        assertTrue(gist.files.isEmpty())
    }

    @Test
    fun hasContentRequiresNonEmptyBody() {
        val blank = GistFile(filename = "b.txt", language = null, type = null, size = 0, content = "", rawUrl = null)
        val filled = GistFile(filename = "f.txt", language = null, type = null, size = 1, content = "x", rawUrl = null)

        assertFalse(blank.hasContent)
        assertTrue(filled.hasContent)
    }
}
