/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.platform.diagnostics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** M1-07：日志脱敏。凭据一旦落盘就是事故，这里必须穷举。 */
class RedactorTest {
    @Test
    fun masksBearerToken() {
        val out = Redactor.redact("Authorization: Bearer ghp_abcdefghijklmnopqrstuvwxyz012345")
        assertFalse(out.contains("abcdefghijklmnop"))
        assertTrue(out.contains("ghp_"))
    }

    @Test
    fun masksTokenFields() {
        val out = Redactor.redact("token=ghp_1234567890abcdefghij")
        assertFalse(out.contains("1234567890abcdefghij"))
    }

    @Test
    fun masksFineGrainedPat() {
        val out = Redactor.redact("pat=github_pat_11ABCDEFG0aBcDeFgHiJkLmNoPqRsTuVwXyZ0123456789")
        assertFalse(out.contains("11ABCDEFG0aBcDeFgHiJkLmNoPqRsTuVwXyZ"))
    }

    @Test
    fun masksUrlCredentials() {
        val out = Redactor.redact("clone https://user:pass@github.com/o/r.git")
        assertFalse(out.contains("user:pass"))
        assertTrue(out.contains("https://***:***@github.com"))
    }

    @Test
    fun masksUserHomePath() {
        val out = Redactor.redact("path=/Users/tommyface/Library/app.apk")
        assertFalse(out.contains("tommyface"))
        assertTrue(out.contains("/Users/***"))
    }

    @Test
    fun keepsShortValuesFullyMasked() {
        assertEquals("***", Redactor.maskValue("abc"))
        assertEquals("abcd***ef", Redactor.maskValue("abcdef"))
    }

    @Test
    fun isIdempotent() {
        val once = Redactor.redact("token=ghp_1234567890abcdefghij")
        assertEquals(once, Redactor.redact(once))
    }

    @Test
    fun leavesOrdinaryTextIntact() {
        val text = "下载完成 · 12.3 MB · 镜像 ghproxy"
        assertEquals(text, Redactor.redact(text))
    }
}
