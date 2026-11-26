/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.platform.diagnostics

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 分层结构化日志（M1-07，移植并强化壳工程的 `StructuredLogger`）。
 *
 * 关键点：
 *  - **所有写入内容都过 [Redactor]**（凭据不得落盘）；
 *  - 按天分文件，保留最近 [keepDays] 天，自动清理；
 *  - 导出时生成一份可读报告（供用户反馈问题）。
 */
class StructuredLogger(
    context: Context,
    private val keepDays: Int = DEFAULT_KEEP_DAYS,
) {
    /** 日志级别。 */
    enum class Level(val label: String) {
        DEBUG("D"),
        INFO("I"),
        WARN("W"),
        ERROR("E"),
    }

    private val logDir: File = File(context.filesDir, DIR_NAME).apply { mkdirs() }

    /** 记录一条日志（自动脱敏）。 */
    fun log(
        level: Level,
        event: String,
        fields: Map<String, String> = emptyMap(),
    ) {
        val line = buildString {
            append(timestamp())
            append(' ')
            append(level.label)
            append(' ')
            append(event)
            fields.forEach { (key, value) ->
                append(' ')
                append(key)
                append('=')
                append(Redactor.redact(value))
            }
        }
        runCatching { File(logDir, fileName()).appendText(line + "\n") }
    }

    fun debug(event: String, fields: Map<String, String> = emptyMap()) = log(Level.DEBUG, event, fields)

    fun info(event: String, fields: Map<String, String> = emptyMap()) = log(Level.INFO, event, fields)

    fun warn(event: String, fields: Map<String, String> = emptyMap()) = log(Level.WARN, event, fields)

    fun error(event: String, fields: Map<String, String> = emptyMap()) = log(Level.ERROR, event, fields)

    /**
     * 导出诊断报告（脱敏后）到 [target]，返回文件路径。
     *
     * 报告包含：应用版本、构建信息、崩溃摘要、最近日志全文（已脱敏）。
     */
    fun export(target: File): File {
        target.parentFile?.mkdirs()
        val builder = StringBuilder()
        builder.append("=== GitHub Android Client 诊断报告 ===\n")
        builder.append("生成时间: ").append(timestamp()).append('\n')
        builder.append("崩溃记录: ").append(crashLog()?.readText().orEmpty().ifBlank { "（无）" }).append('\n')
        builder.append("\n=== 最近日志 ===\n")
        builder.append(recentLogText())
        target.writeText(Redactor.redact(builder.toString()))
        return target
    }

    /** 崩溃日志文件（由 [CrashRecorder] 写入）。 */
    fun crashLog(): File? = File(logDir, CRASH_FILE).takeIf { it.exists() }

    /** 最近 [limitDays] 天的日志全文。 */
    fun recentLogText(limitDays: Int = keepDays): String =
        logDir
            .listFiles { file -> file.name.startsWith(LOG_PREFIX) && file.name.endsWith(LOG_SUFFIX) }
            ?.sortedByDescending { it.name }
            ?.take(limitDays)
            ?.joinToString("\n") { file -> file.readText() }
            .orEmpty()

    /** 清理过期日志（保留最近 [keepDays] 天）。 */
    fun prune() {
        val files =
            logDir.listFiles { file -> file.name.startsWith(LOG_PREFIX) && file.name.endsWith(LOG_SUFFIX) }
                ?: return
        files.sortedByDescending { it.name }.drop(keepDays).forEach { it.delete() }
    }

    private fun fileName(): String = "$LOG_PREFIX${dateStamp()}$LOG_SUFFIX"

    private fun timestamp(): String = TIME_FORMAT.format(Date())

    private fun dateStamp(): String = DATE_FORMAT.format(Date())

    companion object {
        const val DIR_NAME = "diagnostics"
        const val LOG_PREFIX = "log-"
        const val LOG_SUFFIX = ".txt"
        const val CRASH_FILE = "crash.txt"
        const val DEFAULT_KEEP_DAYS = 7

        private val DATE_FORMAT = SimpleDateFormat("yyyyMMdd", Locale.US)
        private val TIME_FORMAT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    }
}

/**
 * 崩溃捕获：安装全局未捕获异常处理器，把堆栈写到诊断目录。
 *
 * 注意：崩溃处理器里**不做任何复杂操作**（不写网络、不加锁），
 * 避免二次崩溃；写完即交回系统默认处理（进程仍会退出）。
 */
object CrashRecorder {
    /** 安装处理器，返回是否成功。 */
    fun install(
        context: Context,
        logger: StructuredLogger,
    ): Boolean =
        runCatching {
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching {
                    val file = File(context.filesDir, "${StructuredLogger.DIR_NAME}/${StructuredLogger.CRASH_FILE}")
                    file.parentFile?.mkdirs()
                    file.writeText(
                        "thread: ${thread.name}\n" +
                            "time: ${System.currentTimeMillis()}\n" +
                            "exception: ${throwable::class.java.name}\n" +
                            "message: ${throwable.message}\n" +
                            "stack:\n${throwable.stackTraceToString()}\n",
                    )
                }
                previous?.uncaughtException(thread, throwable)
            }
            true
        }.getOrDefault(false)
}
