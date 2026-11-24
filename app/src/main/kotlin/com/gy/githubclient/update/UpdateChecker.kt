/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.update

import android.content.Context
import com.gy.githubclient.BuildConfig
import com.gy.githubclient.core.data.network.GitHubJson
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

private const val SHA256_HEX_LENGTH = 64

/** GET /releases/latest 与 /releases 响应（精简字段）。 */
@Serializable
data class ReleaseLatestDto(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("name") val releaseName: String? = null,
    @SerialName("body") val body: String? = null,
    @SerialName("prerelease") val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("assets") val assets: List<ReleaseAssetDto> = emptyList(),
)

/** Release 资产（APK/zip 包）。 */
@Serializable
data class ReleaseAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0,
    /** GitHub 官方完整性摘要（v0.5.3 起自动生成，形如 `sha256:…`）。 */
    @SerialName("digest") val digest: String? = null,
)

/**
 * 语义化版本比较（纯 Kotlin，可单测）。
 * 解析 `v1.2.3` / `1.2.3` → (major, minor, patch)；解析失败视为不可比较（返回 false）。
 */
object SemanticVersion {
    fun parse(text: String): Triple<Int, Int, Int>? {
        val cleaned = text.trim().removePrefix("v").removePrefix("V")
        val parts = cleaned.split(".")
        if (parts.size < 2 || parts.size > 3) return null
        val major = parts[0].toIntOrNull() ?: return null
        val minor = parts[1].toIntOrNull() ?: return null
        val patch = parts.getOrNull(2)?.takeWhile { it.isDigit() }?.toIntOrNull() ?: 0
        return Triple(major, minor, patch)
    }

    /** 远端版本是否严格高于本地版本。任一侧解析失败均返回 false（宁可不提示，不误报）。 */
    fun isNewer(remote: String, local: String): Boolean {
        val r = parse(remote) ?: return false
        val l = parse(local) ?: return false
        val pairs = listOf(r.first to l.first, r.second to l.second, r.third to l.third)
        for ((remotePart, localPart) in pairs) {
            if (remotePart != localPart) return remotePart > localPart
        }
        return false
    }
}

/**
 * 自更新检查器：
 * - 定时请求本仓库 `releases/latest`，带 `If-None-Match`（ETag）做条件请求，
 *   304 时不消耗核心 API 配额（详见 P10 论证 §四.3）。
 * - 结果与 ETag 落 SharedPreferences；无新版本 / 网络失败 / 解析失败一律返回 null（静默）。
 * - 响应解析走 bodyAsText + 显式 serializer，规避 Ktor 3.0.3 真机 ContentNegotiation 缺陷。
 */
class UpdateChecker(
    private val context: Context,
    private val repoOwner: String = REPO_OWNER,
    private val repoName: String = REPO_NAME,
) {
    /** 一次检查得到的新版本信息（仅远程严格高于本地时返回）。 */
    data class UpdateInfo(
        val tagName: String,
        val downloadUrl: String?,
        val htmlUrl: String,
        /** 目标 APK 的 GitHub 官方 sha256（小写十六进制）；null 表示摘要缺失、跳过校验。 */
        val sha256: String?,
        /** 目标 APK 字节数（-1 未知）。 */
        val assetSize: Long,
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Suppress("TooGenericExceptionCaught")
    suspend fun check(includePrerelease: Boolean = false): UpdateInfo? =
        try {
            val client =
                HttpClient(OkHttp) {
                    install(HttpTimeout) {
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 30_000
                        requestTimeoutMillis = 30_000
                    }
                }
            client.use { http -> checkWith(http, includePrerelease) }
        } catch (t: Exception) {
            null // 更新检查失败不影响任何主流程
        }

    private suspend fun checkWith(
        http: HttpClient,
        includePrerelease: Boolean,
    ): UpdateInfo? {
        val response: HttpResponse =
            http.get(releasesPath(includePrerelease)) {
                headers {
                    append(HttpHeaders.Accept, "application/vnd.github+json")
                    append(API_VERSION_HEADER, API_VERSION)
                    prefs.getString(KEY_ETAG, null)?.let { append(HttpHeaders.IfNoneMatch, it) }
                }
            }
        return when {
            response.status.value == 304 -> null // 无更新：ETag 命中，零配额消耗

            response.status == HttpStatusCode.OK -> mapOkResponse(response, includePrerelease)

            else -> null // 4xx/5xx 静默跳过，后台任务不报错
        }
    }

    /** 预发布开关开时改用列表端点（/releases/latest 永不含 prerelease）。 */
    private fun releasesPath(includePrerelease: Boolean) =
        if (includePrerelease) {
            "https://api.github.com/repos/$repoOwner/$repoName/releases?per_page=$PRERELEASE_SCAN_PAGE"
        } else {
            "https://api.github.com/repos/$repoOwner/$repoName/releases/latest"
        }

    private suspend fun mapOkResponse(
        response: HttpResponse,
        includePrerelease: Boolean,
    ): UpdateInfo? {
        response.headers[HttpHeaders.ETag]?.let { etag ->
            prefs.edit().putString(KEY_ETAG, etag).apply()
        }
        val text = response.bodyAsText()
        val dto: ReleaseLatestDto? =
            if (includePrerelease) {
                pickNewestRelease(GitHubJson.decode(ListSerializer(ReleaseLatestDto.serializer()), text))
            } else {
                GitHubJson.decode(ReleaseLatestDto.serializer(), text)
            }
        if (dto == null || !isRemoteNewer(dto)) return null
        val asset = dto.assets.firstOrNull { it.isApkOrZip() }
        return UpdateInfo(
            tagName = dto.tagName,
            downloadUrl = asset?.downloadUrl,
            htmlUrl = dto.htmlUrl,
            sha256 = asset?.officialSha256(),
            assetSize = asset?.size ?: -1L,
        )
    }

    /**
     * v0.5.3 M4：versionCode 权威判定优先。
     * 发布约定：Release body 首行写 `versionCode: N`；
     * 存在该标记时与 BuildConfig.VERSION_CODE 直接比较（不受 tag 命名影响），
     * 缺失时回退 tag 语义版本比较（宁可不提示，不误报）。
     */
    private fun isRemoteNewer(dto: ReleaseLatestDto): Boolean {
        val remoteCode = versionCodeFromReleaseBody(dto.body)
        return if (remoteCode != null) {
            remoteCode > BuildConfig.VERSION_CODE
        } else {
            SemanticVersion.isNewer(dto.tagName, BuildConfig.VERSION_NAME)
        }
    }

    private companion object {
        const val PREFS_NAME = "update_check"
        const val KEY_ETAG = "etag"
        const val API_VERSION_HEADER = "X-GitHub-Api-Version"
        const val API_VERSION = "2026-03-10"

        /** 预发布扫描窗口：列表端点取最近 N 个 Release 参与挑选。 */
        const val PRERELEASE_SCAN_PAGE = 15

        /** 本 App 自身的发布仓库：更新检查的目标。 */
        const val REPO_OWNER = "Tommyface"
        const val REPO_NAME = "github-android-client"
    }
}

/** 该资产是否为可安装目标（APK 优先，zip 兜底）。 */
internal fun ReleaseAssetDto.isApkOrZip(): Boolean = name.endsWith(".apk") || name.endsWith(".zip")

/** 解析 GitHub 官方 digest（形如 `sha256:xxx`）为小写十六进制；非 sha256 算法或缺失返回 null。 */
internal fun ReleaseAssetDto.officialSha256(): String? =
    digest
        ?.takeIf { it.startsWith("sha256:", ignoreCase = true) }
        ?.substringAfter(':')
        ?.trim()
        ?.lowercase()
        ?.takeIf { it.length == SHA256_HEX_LENGTH }

/**
 * 从 Release 列表（含预发布）中挑出「应当升级」的目标版本（纯函数，可单测）：
 * 1. 可解析出 `versionCode: N` 的 Release 中取 N 最大者；
 * 2. 全部无标记时按 published_at 降序取最新。
 */
internal fun pickNewestRelease(releases: List<ReleaseLatestDto>): ReleaseLatestDto? {
    if (releases.isEmpty()) return null
    val withCode = releases.mapNotNull { release -> versionCodeFromReleaseBody(release.body)?.let { release to it } }
    return withCode
        .maxByOrNull { it.second }
        ?.first
        ?: releases
            .filter { it.publishedAt != null }
            .maxByOrNull { it.publishedAt ?: "" }
        ?: releases.first()
}

/**
 * 从 Release body 提取发布约定标记 `versionCode: N`（首部出现即生效）。
 * 兼容冒号/等号与可选空白；提取失败返回 null（回退语义版本判定）。
 * 顶层函数便于单测。
 */
internal fun versionCodeFromReleaseBody(body: String?): Int? {
    val text = body ?: return null
    val match = VERSION_CODE_PATTERN.find(text) ?: return null
    return match.groupValues[1].toIntOrNull()?.takeIf { it > 0 }
}

private val VERSION_CODE_PATTERN = Regex("""versionCode\s*[:=]\s*(\d{1,6})""", RegexOption.IGNORE_CASE)
