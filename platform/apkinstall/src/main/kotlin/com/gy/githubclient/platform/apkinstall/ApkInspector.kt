/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.platform.apkinstall

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.security.MessageDigest

/**
 * APK 与已安装包的事实读取（Android 适配层）。
 *
 * 只负责"把事实取出来"，判定逻辑在 [com.gy.githubclient.core.domain.install.InstallPreconditionChecker]
 * （纯 Kotlin，可穷举测试）。这样分层的好处是：**判定规则可单测，平台耦合被隔离在一个文件里**。
 */
object ApkInspector {
    /** 待安装 APK 的事实（读取失败时字段为 null）。 */
    data class ApkFacts(
        val packageName: String?,
        val versionCode: Long,
        val signatureHex: String?,
        val exists: Boolean,
        val sizeBytes: Long,
    )

    /** 读取待安装 APK 的事实。 */
    fun inspectApk(
        context: Context,
        apkPath: String,
    ): ApkFacts {
        val file = java.io.File(apkPath)
        if (!file.exists() || file.length() <= 0L) {
            return ApkFacts(packageName = null, versionCode = 0L, signatureHex = null, exists = false, sizeBytes = 0L)
        }
        val pm = context.packageManager
        val info =
            pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNING_CERTIFICATES)
                ?: return ApkFacts(
                    packageName = null,
                    versionCode = 0L,
                    signatureHex = null,
                    exists = true,
                    sizeBytes = file.length(),
                )
        return ApkFacts(
            packageName = info.packageName,
            versionCode = info.longVersionCodeCompat(),
            signatureHex = signatureHexOf(info),
            exists = true,
            sizeBytes = file.length(),
        )
    }

    /** 读取"已安装的同包名应用"的事实；未安装时返回 null。 */
    fun inspectInstalled(
        context: Context,
        packageName: String,
    ): InstalledFacts? {
        val pm = context.packageManager
        val info =
            runCatching { pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES) }
                .getOrNull()
                ?: return null
        return InstalledFacts(
            signatureHex = signatureHexOf(info),
            versionCode = info.longVersionCodeCompat(),
        )
    }

    /** 签名证书 SHA-256（小写十六进制）。 */
    fun signatureHexOf(info: PackageInfo): String? {
        val signature =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.signingCertificateHistory?.firstOrNull()
            } else {
                @Suppress("DEPRECATION")
                info.signatures?.firstOrNull()
            } ?: return null
        return MessageDigest
            .getInstance("SHA-256")
            .digest(signature.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    /** 已安装包的事实。 */
    data class InstalledFacts(
        val signatureHex: String?,
        val versionCode: Long,
    )
}

/** `PackageInfo.longVersionCode` 的兼容取值（API 28 以下用 versionCode）。 */
internal fun PackageInfo.longVersionCodeCompat(): Long =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
