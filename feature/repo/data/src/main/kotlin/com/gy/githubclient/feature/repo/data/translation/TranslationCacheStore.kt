/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data.translation

import android.content.Context
import java.security.MessageDigest

/**
 * README 译文缓存（SharedPreferences）：
 * - key = SHA-256(target|text)，避免长文本直接做 key；
 * - 单段译文不会重复请求翻译服务；
 * - 条目超过 [MAX_ENTRIES] 整体清空（README 场景简单可控，避免无界增长）。
 */
class TranslationCacheStore(
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun get(
        text: String,
        targetLanguage: String,
    ): String? = prefs.getString(key(text, targetLanguage), null)

    fun put(
        text: String,
        targetLanguage: String,
        translated: String,
    ) {
        if (prefs.all.size >= MAX_ENTRIES) prefs.edit().clear().apply()
        prefs.edit().putString(key(text, targetLanguage), translated).apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun key(
        text: String,
        targetLanguage: String,
    ): String {
        val digest = MessageDigest.getInstance("SHA-256").digest("$targetLanguage|$text".toByteArray())
        return digest.joinToString("") { byte -> "%02x".format(byte) }
    }

    private companion object {
        const val PREFS_NAME = "readme_translation_cache"
        const val MAX_ENTRIES = 4_000
    }
}
