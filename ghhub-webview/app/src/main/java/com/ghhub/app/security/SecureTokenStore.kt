package com.ghhub.app.security

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * SecureTokenStore：EncryptedSharedPreferences + AES256（方案五·复用策略）。
 * 直接复用现有 Kotlin 代码设计；GitHub Token 永不落明文。
 */
class SecureTokenStore(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val prefs: SharedPreferences = EncryptedSharedPreferences.create(
        context,
        "ghhub_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun saveToken(token: String) {
        prefs.edit().putString(KEY_TOKEN, token).apply()
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).apply()
    }

    /** 脱敏显示：ghp_····f21b */
    fun maskedToken(): String {
        val t = getToken() ?: return "未设置"
        return if (t.length <= 8) "····" else t.take(4) + "····" + t.takeLast(4)
    }

    companion object {
        private const val KEY_TOKEN = "github_token"
    }
}
