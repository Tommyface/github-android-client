/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.data.local

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.gy.githubclient.feature.auth.domain.model.AuthMethod
import com.gy.githubclient.feature.auth.domain.model.TokenChannel

/**
 * 基于 EncryptedSharedPreferences 的 token 安全存储。
 * 主通道与通知通道分字段存放，token 明文永不进入 domain 模型或日志。
 */
class SecureTokenStore(
    context: Context,
) {
    private val masterKey: MasterKey =
        MasterKey
            .Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()

    private val prefs: SharedPreferences =
        EncryptedSharedPreferences.create(
            context,
            PREF_NAME,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    fun getToken(channel: TokenChannel): String? =
        prefs.getString(keyFor(channel), null)?.takeIf { it.isNotEmpty() }

    fun saveToken(
        channel: TokenChannel,
        token: String,
    ) {
        prefs.edit().putString(keyFor(channel), token).apply()
    }

    fun clearToken(channel: TokenChannel) {
        prefs.edit().remove(keyFor(channel)).apply()
    }

    fun clearAll() {
        prefs.edit().clear().apply()
    }

    fun getAuthMethod(): AuthMethod? =
        prefs.getString(KEY_AUTH_METHOD, null)?.let { runCatching { AuthMethod.valueOf(it) }.getOrNull() }

    fun setAuthMethod(method: AuthMethod) {
        prefs.edit().putString(KEY_AUTH_METHOD, method.name).apply()
    }

    /** OAuth 实际授予的 scope（空格分隔原文），PAT 登录不保存。 */
    fun getScopes(): List<String> =
        prefs
            .getString(KEY_SCOPES, null)
            ?.split(' ')
            ?.map { it.trim() }
            ?.filter { it.isNotEmpty() }
            .orEmpty()

    fun saveScopes(scopes: List<String>) {
        prefs.edit().putString(KEY_SCOPES, scopes.joinToString(" ")).apply()
    }

    private fun keyFor(channel: TokenChannel): String =
        when (channel) {
            TokenChannel.PRIMARY -> KEY_PRIMARY_TOKEN
            TokenChannel.NOTIFICATION -> KEY_NOTIFICATION_TOKEN
        }

    private companion object {
        const val PREF_NAME = "auth_secure_prefs"
        const val KEY_PRIMARY_TOKEN = "primary_token"
        const val KEY_NOTIFICATION_TOKEN = "notification_token"
        const val KEY_AUTH_METHOD = "auth_method"
        const val KEY_SCOPES = "oauth_scopes"
    }
}
