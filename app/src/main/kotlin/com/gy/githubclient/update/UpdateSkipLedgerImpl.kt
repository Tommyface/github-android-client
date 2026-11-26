/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.update

import android.content.Context
import com.gy.githubclient.core.domain.update.SkipKey
import com.gy.githubclient.core.domain.update.UpdateSkipLedger
import org.json.JSONArray
import org.json.JSONObject

/**
 * 更新状态账本的 SharedPreferences 实现。
 *
 * 存储内容：
 *  - `skipped`：JSON 数组，每项 `{"version":"…","fingerprint":"…"}`（**按指纹记录**，不按版本号）；
 *  - `last_install_requested_at`：上次点击安装的时间戳（安装 TTL，防装失败死循环）；
 *  - `last_check_at`：上次检查时间戳（检查节流）；
 *  - `throttle_millis`：节流窗口，默认 30 分钟。
 *
 * 迁移自壳工程的 `UpdatePreferences`，但**不使用 Gson**（避免为一个 JSON 引入额外依赖），
 * 改用平台自带的 `org.json`。
 */
class UpdateSkipLedgerImpl(
    context: Context,
) : UpdateSkipLedger {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun skippedKeys(): Set<SkipKey> {
        val raw = prefs.getString(KEY_SKIPPED, null) ?: return emptySet()
        return runCatching {
            val array = JSONArray(raw)
            buildSet {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val version = item.optString("version").takeIf { it.isNotBlank() } ?: continue
                    val fingerprint = item.optString("fingerprint").orEmpty()
                    add(SkipKey(version, fingerprint))
                }
            }
        }.getOrDefault(emptySet())
    }

    override fun recordSkip(key: SkipKey) {
        val array = JSONArray()
        skippedKeys().forEach { existing ->
            array.put(JSONObject().apply {
                put("version", existing.version)
                put("fingerprint", existing.fingerprint)
            })
        }
        array.put(JSONObject().apply {
            put("version", key.version)
            put("fingerprint", key.fingerprint)
        })
        prefs.edit().putString(KEY_SKIPPED, array.toString()).apply()
    }

    override fun lastInstallRequestedAt(): Long? =
        prefs.getLong(KEY_LAST_INSTALL_REQUESTED_AT, 0L).takeIf { it > 0L }

    override fun recordInstallRequested(at: Long) {
        prefs.edit().putLong(KEY_LAST_INSTALL_REQUESTED_AT, at).apply()
    }

    override fun lastCheckAt(): Long = prefs.getLong(KEY_LAST_CHECK_AT, 0L)

    override fun recordCheckAt(at: Long) {
        prefs.edit().putLong(KEY_LAST_CHECK_AT, at).apply()
    }

    override fun throttleMillis(): Long =
        prefs.getLong(KEY_THROTTLE_MILLIS, DEFAULT_THROTTLE_MILLIS).coerceAtLeast(0L)

    fun setThrottleMillis(millis: Long) {
        prefs.edit().putLong(KEY_THROTTLE_MILLIS, millis.coerceAtLeast(0L)).apply()
    }

    private companion object {
        const val PREFS_NAME = "update_state"
        const val KEY_SKIPPED = "skipped"
        const val KEY_LAST_INSTALL_REQUESTED_AT = "last_install_requested_at"
        const val KEY_LAST_CHECK_AT = "last_check_at"
        const val KEY_THROTTLE_MILLIS = "throttle_millis"
        const val DEFAULT_THROTTLE_MILLIS = 30 * 60 * 1000L
    }
}
