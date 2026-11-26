/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.install

/**
 * 安装前置校验结论（M1-08 的纯逻辑部分）。
 *
 * 覆盖安装成立需同时满足三件事（Android 系统强制）：
 *  1. 新包签名与已安装包**一致**，否则 `INSTALL_FAILED_UPDATE_INCOMPATIBLE`；
 *  2. 新包 versionCode **不低于**已安装版本，否则 `INSTALL_FAILED_VERSION_DOWNGRADE`；
 *  3. Android 8.0+ 需授予「安装未知应用」权限（属环境问题，单独判定）。
 *
 * 这里只做**判定**，不执行安装；实际安装仍交系统安装器（不重复造轮子）。
 */
sealed class InstallPrecondition {
    /** 可以进入安装流程。 */
    data class Allowed(val message: String) : InstallPrecondition()

    /** 不允许安装，并给出**可行动**的原因。 */
    data class Rejected(
        val code: Code,
        val message: String,
    ) : InstallPrecondition()

    enum class Code {
        /** 待装包不存在或为空。 */
        APK_MISSING,

        /** 待装包包名与本 App 不一致。 */
        PACKAGE_MISMATCH,

        /** 签名证书指纹不一致。 */
        SIGNATURE_MISMATCH,

        /** 指纹无法读取。 */
        SIGNATURE_UNREADABLE,

        /** versionCode 低于已安装版本。 */
        DOWNGRADE_BLOCKED,

        /** 缺少「安装未知应用」权限。 */
        UNKNOWN_APPS_PERMISSION,
    }
}

/**
 * 安装前置校验的**纯函数**实现（无 Android 依赖，可穷举测试）。
 *
 * 参数均为调用方从 [android.content.pm.PackageManager] 读出的事实，
 * 本对象只负责判定与给出文案。
 */
object InstallPreconditionChecker {
    fun check(
        expectedPackageName: String,
        incomingPackageName: String?,
        apkExists: Boolean,
        apkSizeBytes: Long,
        installedSignatureHex: String?,
        incomingSignatureHex: String?,
        installedVersionCode: Long,
        incomingVersionCode: Long,
        canRequestPackageInstalls: Boolean,
    ): InstallPrecondition {
        if (!apkExists || apkSizeBytes <= 0L) {
            return InstallPrecondition.Rejected(
                InstallPrecondition.Code.APK_MISSING,
                "安装包不存在或为空，请重新下载",
            )
        }
        if (incomingPackageName == null || incomingPackageName != expectedPackageName) {
            return InstallPrecondition.Rejected(
                InstallPrecondition.Code.PACKAGE_MISMATCH,
                "安装包包名与本应用不一致，不可覆盖安装",
            )
        }
        // 未安装旧版本时无需比对签名与版本号。
        if (installedSignatureHex == null) {
            return if (canRequestPackageInstalls) {
                InstallPrecondition.Allowed("校验通过，可安装")
            } else {
                InstallPrecondition.Rejected(
                    InstallPrecondition.Code.UNKNOWN_APPS_PERMISSION,
                    "需授予「允许安装未知应用」权限后才能安装",
                )
            }
        }
        if (incomingSignatureHex == null) {
            return InstallPrecondition.Rejected(
                InstallPrecondition.Code.SIGNATURE_UNREADABLE,
                "无法读取安装包签名，已中止以防覆盖失败",
            )
        }
        if (!normalize(installedSignatureHex).equals(normalize(incomingSignatureHex), ignoreCase = true)) {
            return InstallPrecondition.Rejected(
                InstallPrecondition.Code.SIGNATURE_MISMATCH,
                "签名不一致，覆盖安装必须同签名，已拒绝以防数据丢失",
            )
        }
        if (incomingVersionCode < installedVersionCode) {
            return InstallPrecondition.Rejected(
                InstallPrecondition.Code.DOWNGRADE_BLOCKED,
                "新版本($incomingVersionCode)低于已安装版本($installedVersionCode)，系统禁止降级覆盖安装",
            )
        }
        return if (canRequestPackageInstalls) {
            InstallPrecondition.Allowed("校验通过，可覆盖安装（保留应用数据）")
        } else {
            InstallPrecondition.Rejected(
                InstallPrecondition.Code.UNKNOWN_APPS_PERMISSION,
                "需授予「允许安装未知应用」权限后才能覆盖安装",
            )
        }
    }

    private fun normalize(hex: String): String = hex.lowercase().replace(":", "").replace(" ", "")
}
