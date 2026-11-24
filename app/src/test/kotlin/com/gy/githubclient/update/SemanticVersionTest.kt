/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 语义化版本解析与比较回归：更新检查只在新版本严格高于本地时提示。 */
class SemanticVersionTest {
    @Test
    fun parsesPrefixedAndPlainVersions() {
        assertEquals(Triple(0, 2, 0), SemanticVersion.parse("v0.2.0"))
        assertEquals(Triple(1, 10, 3), SemanticVersion.parse("1.10.3"))
        assertEquals(Triple(2, 0, 0), SemanticVersion.parse("V2.0"))
    }

    @Test
    fun comparesCorrectly() {
        assertTrue(SemanticVersion.isNewer("v0.3.0", "0.2.0"))
        assertTrue(SemanticVersion.isNewer("v0.2.1", "v0.2.0"))
        assertTrue(SemanticVersion.isNewer("1.0.0", "0.99.99"))
        assertFalse(SemanticVersion.isNewer("v0.2.0", "0.2.0")) // 相等不提示
        assertFalse(SemanticVersion.isNewer("0.1.9", "v0.2.0"))
    }

    @Test
    fun unparseableNeverTriggersUpdate() {
        assertFalse(SemanticVersion.isNewer("", "0.2.0"))
        assertFalse(SemanticVersion.isNewer("abc", "0.2.0"))
        assertFalse(SemanticVersion.isNewer("v1.2", "bad-local"))
    }
}

/** v0.5.3 M4 回归：Release body `versionCode:` 发布约定解析。 */
class VersionCodeFromBodyTest {
    @Test
    fun extractsFromBodyFirstLine() {
        assertEquals(8, versionCodeFromReleaseBody("versionCode: 8\n\n更新说明…"))
        assertEquals(12, versionCodeFromReleaseBody("versionCode:12"))
        assertEquals(100, versionCodeFromReleaseBody("说明\nversionCode = 100"))
    }

    @Test
    fun caseInsensitiveAndRejectsInvalid() {
        assertEquals(9, versionCodeFromReleaseBody("VersionCode: 9"))
        assertEquals(null, versionCodeFromReleaseBody("没有标记的 body"))
        assertEquals(null, versionCodeFromReleaseBody("versionCode: 0"))
        assertEquals(null, versionCodeFromReleaseBody(null))
        assertEquals(null, versionCodeFromReleaseBody("versionCode: abc"))
    }
}
