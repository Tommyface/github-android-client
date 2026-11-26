/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.link

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** GitHub 链接解析（ghlink 移植）纯函数测试。 */
class GitHubLinkParserTest {
    @Test
    fun repositoryLinkIsParsed() {
        val link = GitHubLinkParser.parse("https://github.com/square/retrofit")
        assertEquals(GitHubLinkTarget.Repository("square", "retrofit"), link?.target)
    }

    @Test
    fun treeLinkKeepsEncodedSlashInRef() {
        val link = GitHubLinkParser.parse("https://github.com/o/r/tree/release%2Fv1.2/src")
        assertEquals(GitHubLinkTarget.Files("o", "r", "release/v1.2"), link?.target)
    }

    @Test
    fun blobLinkFallsBackToRefRoot() {
        val link = GitHubLinkParser.parse("https://github.com/o/r/blob/main/app/Main.kt")
        assertEquals(GitHubLinkTarget.Files("o", "r", "main"), link?.target)
    }

    @Test
    fun issueAndPullLinksOpenRepositoryPage() {
        assertEquals(
            GitHubLinkTarget.Repository("o", "r"),
            GitHubLinkParser.parse("https://github.com/o/r/issues/42")?.target,
        )
        assertEquals(
            GitHubLinkTarget.Repository("o", "r"),
            GitHubLinkParser.parse("https://github.com/o/r/pull/7")?.target,
        )
    }

    @Test
    fun releaseTagLinkIsParsed() {
        val link = GitHubLinkParser.parse("https://github.com/o/r/releases/tag/v1.2.3")
        assertEquals(GitHubLinkTarget.Releases("o", "r", "v1.2.3"), link?.target)
    }

    @Test
    fun releaseAssetLinkBecomesDownload() {
        val link = GitHubLinkParser.parse("https://github.com/o/r/releases/download/v1/app.apk")
        val target = link?.target as GitHubLinkTarget.Download
        assertEquals("app.apk", target.fileName)
        assertEquals("o", target.owner)
        assertEquals("r", target.repo)
        assertEquals("v1", target.tag)
        assertTrue(target.url.endsWith("/releases/download/v1/app.apk"))
    }

    @Test
    fun codeloadLinkBecomesZipDownload() {
        val link = GitHubLinkParser.parse("https://codeload.github.com/o/r/zip/refs/heads/main")
        val target = link?.target as GitHubLinkTarget.Download
        assertEquals("r-main.zip", target.fileName)
        assertEquals("o", target.owner)
        assertEquals("r", target.repo)
    }

    @Test
    fun commitAndActionsLinksAreRouted() {
        assertEquals(
            GitHubLinkTarget.Commits("o", "r", "abcdef"),
            GitHubLinkParser.parse("https://github.com/o/r/commit/abcdef")?.target,
        )
        assertEquals(
            GitHubLinkTarget.Actions("o", "r"),
            GitHubLinkParser.parse("https://github.com/o/r/actions")?.target,
        )
    }

    @Test
    fun reservedTopLinksMapToAppPages() {
        assertEquals(GitHubLinkTarget.Notifications, GitHubLinkParser.parse("https://github.com/notifications")?.target)
        assertEquals(GitHubLinkTarget.Trending, GitHubLinkParser.parse("https://github.com/trending")?.target)
        assertEquals(GitHubLinkTarget.Gists, GitHubLinkParser.parse("https://github.com/gists")?.target)
    }

    @Test
    fun unknownGithubPagesGoExternal() {
        val link = GitHubLinkParser.parse("https://github.com/o/r/wiki/Home")
        assertTrue(link?.target is GitHubLinkTarget.External)
    }

    @Test
    fun userProfileGoesExternalInsteadOfFakeRepo() {
        val link = GitHubLinkParser.parse("https://github.com/torvalds")
        val target = link?.target
        assertTrue(target is GitHubLinkTarget.External)
        assertEquals("https://github.com/torvalds", (target as GitHubLinkTarget.External).url)
    }

    @Test
    fun customSchemeIsSupported() {
        assertEquals(
            GitHubLinkTarget.Repository("o", "r"),
            GitHubLinkParser.parse("githubclient://repo/o/r")?.target,
        )
        assertEquals(
            GitHubLinkTarget.Releases("o", "r"),
            GitHubLinkParser.parse("githubclient://release/o/r")?.target,
        )
    }

    @Test
    fun shareTextExtractsFirstGithubLink() {
        val link = GitHubLinkParser.parseFromText("看看这个项目 https://github.com/o/r ，很不错")
        assertEquals(GitHubLinkTarget.Repository("o", "r"), link?.target)
    }

    @Test
    fun nonGithubTextIsRejected() {
        assertNull(GitHubLinkParser.parse("kotlin compose"))
        assertNull(GitHubLinkParser.parseFromText("readme github.com/o/r is just text"))
        assertNull(GitHubLinkParser.parse("https://gitlab.com/o/r"))
    }

    @Test
    fun queryAndFragmentDoNotChangeTarget() {
        assertEquals(
            GitHubLinkTarget.Repository("o", "r"),
            GitHubLinkParser.parse("https://github.com/o/r?tab=readme-ov-file#install")?.target,
        )
    }
}
