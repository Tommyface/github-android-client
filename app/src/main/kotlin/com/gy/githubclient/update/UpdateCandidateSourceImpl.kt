/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.update

import com.gy.githubclient.core.data.network.GitHubJson
import com.gy.githubclient.core.domain.update.UpdateCandidate
import com.gy.githubclient.core.domain.update.UpdateCandidateSource
import com.gy.githubclient.core.domain.update.UpdateChannelMerger
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer

/**
 * 双通道更新候选来源（M1-02/M1-03）：
 *
 * - **主通道**：GitHub Releases API（`/releases/latest`，带 ETag 条件请求）；
 * - **兜底通道**：`version.json`（CI 发版时生成，指向同一仓库的 raw 地址）；
 * - 两通道**并行**请求，谁先成功用谁；主通道缺指纹时借兜底通道补齐（见 [UpdateChannelMerger]）。
 *
 * 任一通道失败都不抛异常——更新检查永远不能影响主流程。
 */
class UpdateCandidateSourceImpl(
    private val repoOwner: String = REPO_OWNER,
    private val repoName: String = REPO_NAME,
    private val versionJsonUrl: String = "https://raw.githubusercontent.com/$repoOwner/$repoName/main/version.json",
) : UpdateCandidateSource {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun fetch(): UpdateCandidate? =
        coroutineScope {
            val release = async { runCatching { fetchRelease() }.getOrNull() }
            val versionJson = async { runCatching { fetchVersionJson() }.getOrNull() }
            val results = awaitAll(release, versionJson)
            val primary = results[0] as UpdateCandidate?
            val fallback = results[1] as UpdateCandidate?
            UpdateChannelMerger.merge(primary, fallback)
        }

    private suspend fun fetchRelease(): UpdateCandidate? {
        val client = newClient()
        return client.use { http ->
            val response =
                http.get("https://api.github.com/repos/$repoOwner/$repoName/releases/latest") {
                    headers {
                        append(HttpHeaders.Accept, "application/vnd.github+json")
                        append(API_VERSION_HEADER, API_VERSION)
                    }
                }
            if (response.status != HttpStatusCode.OK) return@use null
            val dto = GitHubJson.decode(ReleaseLatestDto.serializer(), response.bodyAsText()) ?: return@use null
            UpdateCandidateParser.fromRelease(dto)
        }
    }

    private suspend fun fetchVersionJson(): UpdateCandidate? {
        val client = newClient()
        return client.use { http ->
            val response = http.get(versionJsonUrl)
            if (response.status != HttpStatusCode.OK) return@use null
            val dto = GitHubJson.decode(VersionJsonDto.serializer(), response.bodyAsText()) ?: return@use null
            UpdateCandidateParser.fromVersionJson(dto)
        }
    }

    private fun newClient(): HttpClient =
        HttpClient(OkHttp) {
            install(HttpTimeout) {
                connectTimeoutMillis = 15_000
                socketTimeoutMillis = 30_000
                requestTimeoutMillis = 30_000
            }
        }

    private companion object {
        const val API_VERSION_HEADER = "X-GitHub-Api-Version"
        const val API_VERSION = "2026-03-10"
        const val REPO_OWNER = "Tommyface"
        const val REPO_NAME = "github-android-client"
    }
}

/** 供测试使用：把 Release 列表 JSON 解成候选（预发布挑选路径）。 */
internal fun parseReleaseList(text: String): List<ReleaseLatestDto> =
    GitHubJson.decode(ListSerializer(ReleaseLatestDto.serializer()), text).orEmpty()
