package com.ghhub.app.core

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * 更新偏好：节流时间戳 + skip 双键记录 + 可选/强制更新提示策略。
 * 借鉴 gh_auto_updater 的「检查日期持久化」与 checks_rate_limit_secs。
 */
class UpdatePreferences(context: Context) {
    private val prefs = context.getSharedPreferences("ghhub_update_prefs", Context.MODE_PRIVATE)
    private val gson = Gson()

    /** 上次检查时间（毫秒），持久化。 */
    var lastCheckAt: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) = prefs.edit().putLong(KEY_LAST_CHECK, value).apply()

    /** 检查节流窗口（分钟），默认 30。 */
    var throttleMinutes: Int
        get() = prefs.getInt(KEY_THROTTLE, 30)
        set(value) = prefs.edit().putInt(KEY_THROTTLE, value).apply()

    /** 是否处于节流窗口内（借鉴 checks_rate_limit_secs）。 */
    fun isThrottled(now: Long = System.currentTimeMillis()): Boolean =
        now - lastCheckAt < throttleMinutes * 60_000L

    /** skip 双键记录：appId -> 双键集合。 */
    fun getSkipKeys(appId: String): Set<SkipKey> {
        val json = prefs.getString("${KEY_SKIP}_$appId", null) ?: return emptySet()
        val type = object : TypeToken<List<SkipKey>>() {}.type
        return (gson.fromJson<List<SkipKey>>(json, type) ?: emptyList()).toSet()
    }

    fun addSkipKey(appId: String, key: SkipKey) {
        val set = getSkipKeys(appId).toMutableSet()
        set.add(key)
        prefs.edit().putString("${KEY_SKIP}_$appId", gson.toJson(set.toList())).apply()
    }

    companion object {
        private const val KEY_LAST_CHECK = "last_check_at"
        private const val KEY_THROTTLE = "throttle_minutes"
        private const val KEY_SKIP = "skip_keys_"
    }
}
