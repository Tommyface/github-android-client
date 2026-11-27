/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data.remote

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.mutation.MutationResult
import com.gy.githubclient.core.mutation.MutationSpec
import com.gy.githubclient.feature.repo.domain.model.CreatedRepository
import com.gy.githubclient.feature.repo.domain.model.UploadTarget
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod

/**
 * 仓库写操作 API（M2-05 / M2-07 / M2-08）。
 *
 * 全部走 [GitHubApiClient.mutate] 统一信封：错误分类、幂等重试、审计留痕。
 * 危险操作（改可见性、归档、删除）标记 `dangerous = true`，由 UI 决定是否二次确认。
 */
class GitHubWriteApi(
    private val client: GitHubApiClient,
) {
    /**
     * 修改仓库设置（简介 / 可见性 / 归档）。
     *
     * 只传需要变更的字段（GitHub 的 PATCH 语义）。
     */
    suspend fun updateRepository(
        owner: String,
        name: String,
        description: String? = null,
        private: Boolean? = null,
        archived: Boolean? = null,
    ): MutationResult<Unit> {
        val body =
            buildString {
                append("{")
                description?.let { append("\"description\":${it.asJsonString()},") }
                private?.let { append("\"private\":$it,") }
                archived?.let { append("\"archived\":$it,") }
                if (isNotEmpty() && last() == ',') setLength(length - 1)
                append("}")
            }
        val dangerous = private != null || archived != null
        return client.mutate(
            spec =
                MutationSpec(
                    action = "repo.update",
                    target = "$owner/$name",
                    idempotent = true,
                    dangerous = dangerous,
                ),
            method = HttpMethod.Patch,
            path = "/repos/$owner/$name",
            body = body,
        ) { _: HttpResponse -> Unit }
    }

    /** 删除仓库（不可撤销；UI 必须二次确认并要求输入仓库名）。 */
    suspend fun deleteRepository(
        owner: String,
        name: String,
    ): MutationResult<Unit> =
        client.mutate(
            spec =
                MutationSpec(
                    action = "repo.delete",
                    target = "$owner/$name",
                    idempotent = true,
                    dangerous = true,
                ),
            method = HttpMethod.Delete,
            path = "/repos/$owner/$name",
        ) { _: HttpResponse -> Unit }

    /** 创建仓库（不可幂等：失败时不自动重试，避免重复建库）。 */
    suspend fun createRepository(
        name: String,
        description: String?,
        private: Boolean,
    ): MutationResult<CreatedRepository> {
        val body =
            buildString {
                append("{\"name\":${name.asJsonString()}")
                description?.let { append(",\"description\":${it.asJsonString()}") }
                append(",\"private\":$private")
                append("}")
            }
        return client.mutate(
            spec =
                MutationSpec(
                    action = "repo.create",
                    target = name,
                    idempotent = false,
                    dangerous = false,
                ),
            method = HttpMethod.Post,
            path = "/user/repos",
            body = body,
        ) { response -> response.createdRepository() }
    }

    /**
     * 上传文件到仓库指定路径（Contents API）。
     *
     * @param contentBase64 文件内容的 base64（GitHub Contents API 要求）
     * @param message 提交信息
     * @param branch 目标分支；为空时用仓库默认分支
     */
    suspend fun uploadFile(
        target: UploadTarget,
        contentBase64: String,
        message: String,
    ): MutationResult<Unit> {
        val owner = target.owner
        val name = target.repository
        val path = target.path
        val branch = target.branch
        val body =
            buildString {
                append("{\"message\":${message.asJsonString()}")
                append(",\"content\":${contentBase64.asJsonString()}")
                branch?.let { append(",\"branch\":${it.asJsonString()}") }
                append("}")
            }
        return client.mutate(
            spec =
                MutationSpec(
                    action = "repo.upload_file",
                    target = "$owner/$name:$path",
                    idempotent = false,
                    dangerous = false,
                ),
            method = HttpMethod.Put,
            path = "/repos/$owner/$name/contents/$path",
            body = body,
        ) { _: HttpResponse -> Unit }
    }
}

/** 从响应体解析创建结果（容错：字段缺失时给出可读占位）。 */
private suspend fun HttpResponse.createdRepository(): CreatedRepository {
    val text = bodyAsTextOrEmpty()
    val fullName =
        Regex("\"full_name\"\\s*:\\s*\"([^\"]+)\"")
            .find(text)
            ?.groupValues
            ?.get(1)
            .orEmpty()
    val htmlUrl =
        Regex("\"html_url\"\\s*:\\s*\"([^\"]+)\"")
            .find(text)
            ?.groupValues
            ?.get(1)
            .orEmpty()
    val isPrivate = Regex("\"private\"\\s*:\\s*(true|false)").find(text)?.groupValues?.get(1) == "true"
    return CreatedRepository(fullName = fullName, htmlUrl = htmlUrl, private = isPrivate)
}

private suspend fun HttpResponse.bodyAsTextOrEmpty(): String =
    runCatching { bodyAsText() }.getOrDefault("")

/** 最小 JSON 字符串转义（只处理引号、反斜杠与控制字符）。 */
internal fun String.asJsonString(): String {
    val escaped =
        this
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    return "\"$escaped\""
}
