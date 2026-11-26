package com.ghhub.app.log

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 结构化日志条目（JSONL，一行一条）。 */
data class LogEntry(
    @SerializedName("ts") val timestamp: String,
    @SerializedName("level") val level: String,
    @SerializedName("event") val event: String,
    @SerializedName("detail") val detail: Map<String, Any?> = emptyMap()
)

/**
 * 下载链路全量结构化日志 + 一键导出（issue #41）。
 * 不照搬 githup 116 方法里的全套诊断桥，只保留排查下载链路的最小集合。
 */
class StructuredLogger(context: Context) {
    private val logFile = File(context.filesDir, "ghhub_download.jsonl")
    private val gson = Gson()
    private val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }

    fun log(level: Level, event: String, detail: Map<String, Any?> = emptyMap()) {
        val entry = LogEntry(fmt.format(Date()), level.name, event, detail)
        try {
            logFile.appendText(gson.toJson(entry) + "\n")
        } catch (e: Exception) {
            // 日志写入失败不应中断主流程
        }
    }

    fun readAll(): List<LogEntry> = try {
        logFile.takeIf { it.exists() }?.readLines()
            ?.mapNotNull { runCatching { gson.fromJson(it, LogEntry::class.java) }.getOrNull() }
            ?: emptyList()
    } catch (e: Exception) { emptyList() }

    fun exportFile(): File = logFile

    enum class Level { INFO, WARN, ERROR, DEBUG }

    companion object {
        @Volatile private var instance: StructuredLogger? = null
        fun get(context: Context): StructuredLogger =
            instance ?: synchronized(this) { instance ?: StructuredLogger(context.applicationContext).also { instance = it } }
    }
}
