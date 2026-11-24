/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.data.download

import java.security.MessageDigest

/**
 * APK 签名指纹校验（v0.5.3 M3，对标 Obtainium 的「公布签名指纹 + 哈希校验」实践）：
 * 下载完成的 .apk 在引导安装前比对签名证书 SHA-256 与内置信任指纹，
 * 不匹配即删除——拦截「SHA-256 摘要缺失场景下镜像/代理返回被替换的 APK」。
 *
 * 纯函数部分（hex 计算与比对）可 JVM 单测；Android 侧组装在 DownloadWorker。
 */
object ApkSignatureVerifier {
    /**
     * 内置信任的 APK 签名证书 SHA-256（debug keystore，自 v0.1.0 起六版连续不变）。
     * 与 verify.sh / 交付报告中的签名指纹一致，覆盖安装依赖它保持稳定。
     */
    const val EXPECTED_CERT_SHA256 = "59c6360807fc8eb3700f067093cdf1ca2c8e2d4aa026ebcd353a3aea4ed8cdb8"

    /** 证书字节 → 小写十六进制 SHA-256。 */
    fun certificateSha256Hex(certificateBytes: ByteArray): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest(certificateBytes)
            .joinToString("") { "%02x".format(it) }

    /**
     * 比对证书指纹与期望指纹。
     * 容错：忽略大小写与冒号/空格分隔符；期望指纹为空视为未配置校验（返回 true，不阻断）。
     */
    fun matches(
        certificateHex: String,
        expected: String = EXPECTED_CERT_SHA256,
    ): Boolean {
        if (expected.isBlank()) return true
        if (certificateHex.isBlank()) return false
        val normalize = { s: String -> s.lowercase().replace(":", "").replace(" ", "") }
        return normalize(certificateHex) == normalize(expected)
    }
}
