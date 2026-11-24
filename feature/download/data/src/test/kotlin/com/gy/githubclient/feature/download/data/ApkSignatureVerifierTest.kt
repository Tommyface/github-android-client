/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.data

import com.gy.githubclient.feature.download.data.download.ApkSignatureVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 回归（v0.5.3 M3）：APK 签名指纹计算与比对容错。 */
class ApkSignatureVerifierTest {
    @Test
    fun certificateSha256HexMatchesKnownVector() {
        // SHA-256("") = e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855（FIPS 公开测试向量）
        val hex = ApkSignatureVerifier.certificateSha256Hex(ByteArray(0))
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", hex)
    }

    @Test
    fun hexIsDeterministicAndLowercase() {
        val bytes = byteArrayOf(0x59.toByte(), 0xc6.toByte(), 0x08.toByte())
        val hex = ApkSignatureVerifier.certificateSha256Hex(bytes)
        assertTrue("输出恒为 64 位小写十六进制", hex.length == 64 && hex == hex.lowercase())
        assertEquals("同输入同输出", hex, ApkSignatureVerifier.certificateSha256Hex(bytes))
    }

    @Test
    fun matchesExpectedFingerprintIgnoringCaseAndSeparators() {
        val expected = ApkSignatureVerifier.EXPECTED_CERT_SHA256
        assertTrue(ApkSignatureVerifier.matches(expected))
        assertTrue(ApkSignatureVerifier.matches(expected.uppercase()))
        assertTrue(ApkSignatureVerifier.matches(expected.chunked(2).joinToString(":")))
        assertFalse(ApkSignatureVerifier.matches("deadbeef" + expected.drop(8)))
        assertFalse(ApkSignatureVerifier.matches(""))
    }

    @Test
    fun blankExpectedDisablesVerification() {
        assertTrue(ApkSignatureVerifier.matches("anything", expected = ""))
    }
}
