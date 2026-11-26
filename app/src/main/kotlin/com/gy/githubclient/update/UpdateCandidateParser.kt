/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.update

import com.gy.githubclient.core.domain.update.UpdateCandidate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * version.json 备用通道（无 Release 权限 / Release API 不可用时的兜底）。
 *
 * 由 CI 在发版时生成，字段与 `tools/verify_version.py` 的校验口径一致：
 * `version` / `versionCode` / `fingerprint` / `sha256` / `size` / `apk` / `publishedAt`。
 */
@Serializable
data class VersionJsonDto(
    val version: String = "",
    val versionCode: Int? = null,
    /** 产物 SHA-256（优先取 fingerprint，兼容旧字段名）。 */
    val fingerprint: String? = null,
    val sha256: String? = null,
    val size: Long = -1L,
    val apk: String? = null,
    @SerialName("publishedAt") val publishedAt: String? = null,
)

/**
 * 解析与归一化（纯函数，便于单测）：
 *
 * - Release 通道：版本号取 tag（去掉 `v` 前缀），versionCode 取 Release body 的 `versionCode: N` 约定，
 *   指纹取**目标资产的官方 digest**（`sha256:…`），不使用 `target_commitish`（那与产物无关）。
 * - version.json 通道：字段直读，指纹优先 `fingerprint`，兼容 `sha256`。
 */
object UpdateCandidateParser {
    private const val SHA256_HEX_LENGTH = 64

    /** Release → 候选。缺少版本号时返回 null（不可比较）。 */
    fun fromRelease(dto: ReleaseLatestDto): UpdateCandidate? {
        val version = dto.tagName.trim().removePrefix("v").takeIf { it.isNotBlank() } ?: return null
        val asset = dto.assets.firstOrNull { it.isApkOrZip() }
        return UpdateCandidate(
            version = version,
            versionCode = versionCodeFromReleaseBody(dto.body),
            fingerprint = asset?.officialSha256(),
            downloadUrl = asset?.downloadUrl ?: dto.htmlUrl.takeIf { it.isNotBlank() },
            assetSize = asset?.size ?: -1L,
            notes =
                dto.body
                    .orEmpty()
                    .lines()
                    .map { it.trim().removePrefix("- ") }
                    .filter { it.isNotBlank() }
                    .take(8),
            channel = UpdateCandidate.CHANNEL_RELEASE,
        )
    }

    /** version.json → 候选。 */
    fun fromVersionJson(dto: VersionJsonDto): UpdateCandidate? {
        val version = dto.version.trim().removePrefix("v").takeIf { it.isNotBlank() } ?: return null
        val fingerprint = dto.fingerprint?.trim()?.lowercase()?.takeIf { it.length == SHA256_HEX_LENGTH }
            ?: dto.sha256?.trim()?.lowercase()?.takeIf { it.length == SHA256_HEX_LENGTH }
        return UpdateCandidate(
            version = version,
            versionCode = dto.versionCode,
            fingerprint = fingerprint,
            downloadUrl = dto.apk,
            assetSize = dto.size,
            channel = UpdateCandidate.CHANNEL_VERSION_JSON,
        )
    }
}
