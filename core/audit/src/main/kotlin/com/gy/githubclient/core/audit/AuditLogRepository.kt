/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.audit

import com.gy.githubclient.core.mutation.AuditEntry
import com.gy.githubclient.core.mutation.AuditSink
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** 默认展示的审计条数。 */
const val DEFAULT_AUDIT_LIMIT = 50

/** 审计查询接口：UI 观察最近写操作，可清空。 */
interface AuditLogRepository {
    fun observeRecent(limit: Int = DEFAULT_AUDIT_LIMIT): Flow<List<AuditRecord>>

    suspend fun clear()
}

/** 审计写入接口：实现 [AuditSink]，由统一网络层调用。 */
class RoomAuditSink(
    private val dao: AuditLogDao,
) : AuditSink {
    override suspend fun record(entry: AuditEntry) {
        dao.insert(
            AuditLogEntity(
                action = entry.action,
                target = entry.target,
                success = entry.success,
                statusCode = entry.statusCode,
                attempts = entry.attempts,
                requestId = entry.requestId,
                message = entry.message,
                timestampMillis = entry.timestampMillis,
            ),
        )
        dao.trimTo(MAX_ROWS)
    }

    private companion object {
        const val MAX_ROWS = 500
    }
}

/** 基于 Room 的审计查询实现。 */
class RoomAuditLogRepository(
    private val dao: AuditLogDao,
) : AuditLogRepository {
    override fun observeRecent(limit: Int): Flow<List<AuditRecord>> =
        dao.observeRecent(limit).map { entities -> entities.map { it.toRecord() } }

    override suspend fun clear() = dao.clear()
}
