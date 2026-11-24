/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.data.download

import java.io.File
import java.security.MessageDigest

/** 文件 SHA-256 校验器：流式计算，供下载完成后的完整性核对。 */
object Sha256Verifier {
    private const val BUFFER_SIZE = 64 * 1024

    /** 计算文件 SHA-256（十六进制小写）。 */
    fun digest(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                md.update(buffer, 0, read)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    /** 校验：期望摘要与实际一致（忽略大小写）返回 true；expected 为空跳过校验视为通过。 */
    fun verify(
        file: File,
        expected: String?,
    ): Boolean =
        when {
            expected.isNullOrBlank() -> true
            else -> digest(file).equals(expected.trim(), ignoreCase = true)
        }
}
