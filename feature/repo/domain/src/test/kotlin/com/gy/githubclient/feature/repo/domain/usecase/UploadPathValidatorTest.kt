/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M2-08：上传路径白名单校验（路径穿越防护）。 */
class UploadPathValidatorTest {
    @Test
    fun acceptsNormalPath() {
        val result = UploadPathValidator.validate("docs/README.md")
        assertTrue(result is UploadPathValidator.Result.Valid)
        assertEquals("docs/README.md", (result as UploadPathValidator.Result.Valid).normalizedPath)
    }

    @Test
    fun stripsLeadingDotSlash() {
        val result = UploadPathValidator.validate("./src/main.kt")
        assertEquals("src/main.kt", (result as UploadPathValidator.Result.Valid).normalizedPath)
    }

    @Test
    fun rejectsBlankPath() {
        assertTrue(UploadPathValidator.validate("   ") is UploadPathValidator.Result.Invalid)
    }

    @Test
    fun rejectsAbsolutePath() {
        assertTrue(UploadPathValidator.validate("/etc/passwd") is UploadPathValidator.Result.Invalid)
    }

    @Test
    fun rejectsParentTraversal() {
        val result = UploadPathValidator.validate("../../etc/passwd")
        assertTrue(result is UploadPathValidator.Result.Invalid)
        assertTrue((result as UploadPathValidator.Result.Invalid).reason.contains(".."))
    }

    @Test
    fun rejectsTraversalInMiddle() {
        assertTrue(UploadPathValidator.validate("a/../../b") is UploadPathValidator.Result.Invalid)
    }

    @Test
    fun rejectsEmptySegment() {
        assertTrue(UploadPathValidator.validate("a//b") is UploadPathValidator.Result.Invalid)
    }

    @Test
    fun rejectsDotSegment() {
        assertTrue(UploadPathValidator.validate("a/./b") is UploadPathValidator.Result.Invalid)
    }
}
