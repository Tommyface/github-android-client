/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.update

import com.gy.githubclient.core.domain.update.UpdateCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** M1-02：候选解析（Release 通道与 version.json 通道）。 */
class UpdateCandidateParserTest {
    @Test
    fun releaseProvidesVersionCodeAndFingerprint() {
        val dto =
            ReleaseLatestDto(
                tagName = "v0.19.0",
                htmlUrl = "https://github.com/o/r/releases/tag/v0.19.0",
                body = "versionCode: 21\n- 新增指纹双信号",
                assets =
                    listOf(
                        ReleaseAssetDto(
                            name = "app.apk",
                            downloadUrl = "https://github.com/o/r/releases/download/v0.19.0/app.apk",
                            size = 1234L,
                            digest = "sha256:" + "a".repeat(64),
                        ),
                    ),
            )
        val candidate = UpdateCandidateParser.fromRelease(dto)
        assertNotNull(candidate)
        assertEquals("0.19.0", candidate?.version)
        assertEquals(21, candidate?.versionCode)
        assertEquals("a".repeat(64), candidate?.fingerprint)
        assertEquals(1234L, candidate?.assetSize)
        assertEquals(UpdateCandidate.CHANNEL_RELEASE, candidate?.channel)
    }

    @Test
    fun releaseWithoutDigestHasNoFingerprint() {
        val dto = ReleaseLatestDto(tagName = "v0.19.0", body = "versionCode: 21")
        val candidate = UpdateCandidateParser.fromRelease(dto)
        assertNull(candidate?.fingerprint)
    }

    @Test
    fun releaseWithoutTagIsUnusable() {
        assertNull(UpdateCandidateParser.fromRelease(ReleaseLatestDto(tagName = "")))
    }

    @Test
    fun versionJsonIsParsed() {
        val dto =
            VersionJsonDto(
                version = "0.19.0",
                versionCode = 21,
                sha256 = "b".repeat(64),
                size = 4321L,
                apk = "https://example.com/app.apk",
            )
        val candidate = UpdateCandidateParser.fromVersionJson(dto)
        assertEquals("0.19.0", candidate?.version)
        assertEquals(21, candidate?.versionCode)
        assertEquals("b".repeat(64), candidate?.fingerprint)
        assertEquals(4321L, candidate?.assetSize)
        assertEquals(UpdateCandidate.CHANNEL_VERSION_JSON, candidate?.channel)
    }

    @Test
    fun versionJsonPrefersFingerprintField() {
        val dto = VersionJsonDto(version = "0.19.0", fingerprint = "c".repeat(64), sha256 = "d".repeat(64))
        assertEquals("c".repeat(64), UpdateCandidateParser.fromVersionJson(dto)?.fingerprint)
    }

    @Test
    fun versionJsonIgnoresMalformedDigest() {
        val dto = VersionJsonDto(version = "0.19.0", fingerprint = "not-a-sha256")
        assertNull(UpdateCandidateParser.fromVersionJson(dto)?.fingerprint)
    }
}
