package com.ghhub.app.update

import com.ghhub.app.core.UpdateDecider
import com.ghhub.app.core.UpdatePreferences
import com.ghhub.app.core.UpdateState
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** version.json 通道（githup 双通道机制，我方复用）。 */
data class VersionJson(
    @SerializedName("version") val version: String,
    @SerializedName("fingerprint") val fingerprint: String,
    @SerializedName("changelog") val changelog: List<String> = emptyList(),
    @SerializedName("apk_url") val apkUrl: String? = null
)

/** GitHub Release 通道返回的最小信息。 */
data class ReleaseInfo(
    val tagName: String,
    val fingerprint: String,
    val changelog: List<String>,
    val apkUrl: String?
)

/**
 * 双通道并行检查：Release API + version.json，谁先成功用谁（方案 6.2）。
 * 两条通道都失败时返回 null → 上层置 Unknown，静默。
 */
class UpdateChecker(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build(),
    private val gson: com.google.gson.Gson = com.google.gson.Gson()
) {

    suspend fun check(
        repo: String,
        versionJsonUrl: String,
        token: String? = null
    ): UpdateDecider.RemoteInfo? = withContext(Dispatchers.IO) {
        val release = async { fetchRelease(repo, token) }
        val versionJson = async { fetchVersionJson(versionJsonUrl) }

        // 谁先成功用谁（按完成顺序取第一个非 null）
        val results = listOf(release, versionJson)
        for (defer in results) {
            val r = defer.await()
            if (r != null) return@withContext r
        }
        null
    }

    private fun fetchVersionJson(url: String): UpdateDecider.RemoteInfo? = try {
        val req = Request.Builder().url(url).header("Accept", "application/json").build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val vj = gson.fromJson(body, VersionJson::class.java) ?: return null
            UpdateDecider.RemoteInfo(vj.version, vj.fingerprint, vj.changelog)
        }
    } catch (e: Exception) {
        null
    }

    private fun fetchRelease(repo: String, token: String?): UpdateDecider.RemoteInfo? = try {
        val url = "https://api.github.com/repos/$repo/releases/latest"
        val b = Request.Builder().url(url)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "GHHub/0.1.0")
        if (!token.isNullOrBlank()) b.header("Authorization", "Bearer $token")
        client.newCall(b.build()).execute().use { resp ->
            if (!resp.isSuccessful) return null
            val body = resp.body?.string() ?: return null
            val obj = com.google.gson.JsonParser.parseString(body).asJsonObject
            val tag = obj.get("tag_name")?.asString ?: return null
            // 指纹优先取 assets 中的 *.sha256 / *.txt 附件注释；缺失则用 tag 的 sha 兜底字段
            val fingerprint = obj.get("target_commitish")?.asString?.take(8) ?: tag
            val notes = obj.get("body")?.asString ?: ""
            val changelog = notes.lines().map { it.trim().removePrefix("- ") }.filter { it.isNotBlank() }.take(8)
            UpdateDecider.RemoteInfo(tag, fingerprint, changelog)
        }
    } catch (e: Exception) {
        null
    }
}
