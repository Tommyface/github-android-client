/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.mutation

/** 本地审计记录：每个写操作都留痕，设置页可查看与导出。 */
data class AuditEntry(
    val action: String,
    val target: String,
    val success: Boolean,
    val statusCode: Int? = null,
    val message: String? = null,
    val timestampMillis: Long,
    /** 实际尝试次数（含首次）。 */
    val attempts: Int = 1,
    /** GitHub 响应头 X-GitHub-Request-Id，便于向官方排查。 */
    val requestId: String? = null,
)

/** 审计落库接口；Room 实现放在数据层，domain 只依赖接口。 */
fun interface AuditSink {
    suspend fun record(entry: AuditEntry)
}

object NoOpAuditSink : AuditSink {
    override suspend fun record(entry: AuditEntry) = Unit
}

/** 写操作结果封装，避免各页面各写一套 try/catch。 */
sealed interface MutationResult<out T> {
    data class Success<T>(
        val value: T,
    ) : MutationResult<T>

    data class Failure(
        val error: MutationError,
    ) : MutationResult<Nothing>
}
