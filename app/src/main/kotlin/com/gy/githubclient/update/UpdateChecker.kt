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

/** GET /releases/latest 响应（精简字段）。 */
@Serializable
data class ReleaseLatestDto(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("name") val releaseName: String? = null,
    @SerialName("assets") val assets: List<ReleaseAssetDto> = emptyList(),
)

/** Release 资产（APK/zip 包）。 */
@Serializable
data class ReleaseAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val downloadUrl: String = "",
    val size: Long = 0,
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
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    @Suppress("TooGenericExceptionCaught")
    suspend fun check(): UpdateInfo? =
        try {
            val client =
                HttpClient(OkHttp) {
                    install(HttpTimeout) {
                        connectTimeoutMillis = 15_000
                        socketTimeoutMillis = 30_000
                        requestTimeoutMillis = 30_000
                    }
                }
            client.use { http -> checkWith(http) }
        } catch (t: Exception) {
            null // 更新检查失败不影响任何主流程
        }

    private suspend fun checkWith(http: HttpClient): UpdateInfo? {
        val response: HttpResponse =
            http.get("https://api.github.com/repos/$repoOwner/$repoName/releases/latest") {
                headers {
                    append(HttpHeaders.Accept, "application/vnd.github+json")
                    append(API_VERSION_HEADER, API_VERSION)
                    prefs.getString(KEY_ETAG, null)?.let { append(HttpHeaders.IfNoneMatch, it) }
                }
            }
        return when {
            response.status.value == 304 -> null // 无更新：ETag 命中，零配额消耗

            response.status == HttpStatusCode.OK -> mapOkResponse(response)

            else -> null // 4xx/5xx 静默跳过，后台任务不报错
        }
    }

    private suspend fun mapOkResponse(response: HttpResponse): UpdateInfo? {
        response.headers[HttpHeaders.ETag]?.let { etag ->
            prefs.edit().putString(KEY_ETAG, etag).apply()
        }
        val dto = GitHubJson.decode(ReleaseLatestDto.serializer(), response.bodyAsText())
        if (!SemanticVersion.isNewer(dto.tagName, BuildConfig.VERSION_NAME)) return null
        return UpdateInfo(
            tagName = dto.tagName,
            downloadUrl =
                dto.assets
                    .firstOrNull { it.name.endsWith(".apk") || it.name.endsWith(".zip") }
                    ?.downloadUrl,
            htmlUrl = dto.htmlUrl,
        )
    }

    private companion object {
        const val PREFS_NAME = "update_check"
        const val KEY_ETAG = "etag"
        const val API_VERSION_HEADER = "X-GitHub-Api-Version"
        const val API_VERSION = "2026-03-10"

        /** 本 App 自身的发布仓库：更新检查的目标。 */
        const val REPO_OWNER = "Tommyface"
        const val REPO_NAME = "github-android-client"
    }
}
