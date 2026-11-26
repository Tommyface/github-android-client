package com.ghhub.app.apps

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/** 多应用版本管理仓库（S4，差异化核心）：持久化「我下载过的 GitHub 应用」。 */
class AppRegistry(context: Context) {
    private val prefs = context.getSharedPreferences("ghhub_apps", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun all(): List<ManagedApp> {
        val json = prefs.getString(KEY_APPS, null) ?: return seed()
        val type = object : TypeToken<List<ManagedApp>>() {}.type
        return runCatching { gson.fromJson<List<ManagedApp>>(json, type) }.getOrNull() ?: seed()
    }

    fun save(apps: List<ManagedApp>) {
        prefs.edit().putString(KEY_APPS, gson.toJson(apps)).apply()
    }

    fun add(app: ManagedApp) {
        val list = all().toMutableList()
        if (list.none { it.id == app.id }) { list.add(app); save(list) }
    }

    fun remove(appId: String) = save(all().filterNot { it.id == appId })

    /** 示例数据（与原型/设计稿一致），首次启动时写入。 */
    private fun seed(): List<ManagedApp> = listOf(
        ManagedApp("githup", "githup（Buwrt）", "Buwrt/githup", "1.2.20", "9f3ac21b",
            "https://raw.githubusercontent.com/Buwrt/githup/main/version.json"),
        ManagedApp("appupdater", "AppUpdater Demo", "jenly1314/AppUpdater", "1.4.2", "b71d0e4a",
            "https://raw.githubusercontent.com/jenly1314/AppUpdater/master/version.json"),
        ManagedApp("licai", "licai-ledger PWA", "glorywork/licai-ledger", "3.2.0", "c31fa802",
            "https://raw.githubusercontent.com/glorywork/licai-ledger/main/version.json")
    ).also { save(it) }

    companion object {
        private const val KEY_APPS = "managed_apps"
    }
}
