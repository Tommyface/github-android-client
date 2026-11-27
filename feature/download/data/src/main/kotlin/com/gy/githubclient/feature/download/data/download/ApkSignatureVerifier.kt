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
     * **正式签名证书**的 SHA-256 指纹（M0-08）。
     *
     * 该密钥由 `openssl` 新生成、仅以加密形式存放于 GitHub Actions Secrets
     * （`GHC_KEYSTORE_B64` 等），明文 keystore 不入库；本指纹同时公示于 README 与 SECURITY.md，
     * 供用户核验下载包。
     *
     * 作用范围：App 下载**自身更新包**时，校验其签名证书必须与本指纹一致，
     * 否则视为「不是本 App 的正式包」并拒绝安装（防镜像/代理返回被替换的 APK）。
     */
    const val RELEASE_CERT_SHA256 = "e86de404352ba7a1a03f3ed80565a6fd56d9f50672fbf106db160d796c62cb77"

    /**
     * **调试签名证书**的 SHA-256 指纹。
     *
     * `app/debug.keystore` 是仓库内入库的公开调试密钥（口令 `android`），
     * 仅用于内部覆盖安装调试，**绝不可用于正式分发**。
     * debug 构建的自更新包由它签名，因此 debug 变体使用本指纹。
     */
    const val DEBUG_CERT_SHA256 = "59c6360807fc8eb3700f067093cdf1ca2c8e2d4aa026ebcd353a3aea4ed8cdb8"

    /**
     * 按构建变体取期望指纹。
     *
     * 判定依据用运行时 `FLAG_DEBUGGABLE`，而不是编译期 BuildConfig：
     * 这样数据层无需依赖 app 模块，也避免"测试包用 release 指纹导致自更新全被拒"。
     */
    fun expectedFor(debuggable: Boolean): String =
        if (debuggable) DEBUG_CERT_SHA256 else RELEASE_CERT_SHA256

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
        expected: String,
    ): Boolean {
        if (expected.isBlank()) return true
        if (certificateHex.isBlank()) return false
        val normalize = { s: String -> s.lowercase().replace(":", "").replace(" ", "") }
        return normalize(certificateHex) == normalize(expected)
    }
}
