/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.platform.apkinstall

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.gy.githubclient.core.domain.install.InstallFacts
import com.gy.githubclient.core.domain.install.InstallPrecondition
import com.gy.githubclient.core.domain.install.InstallPreconditionChecker
import java.io.File

/**
 * 安装守卫（Android 适配层）。
 *
 * 职责：读取事实 → 交给纯逻辑判定 → 返回结论与**可执行的下一步**。
 * 不做安装动作本身（安装由系统安装器负责，不重复造轮子）。
 */
class InstallGuard(
    private val context: Context,
) {
    /** 检查结论。 */
    sealed class Result {
        /** 可以安装。 */
        data class Allowed(val message: String) : Result()

        /** 不允许安装；[action] 指出用户下一步该做什么。 */
        data class Rejected(
            val code: InstallPrecondition.Code,
            val message: String,
            val action: Action,
        ) : Result()

        /** 拒绝后建议的动作。 */
        enum class Action {
            /** 授予「安装未知应用」权限后重试。 */
            GRANT_UNKNOWN_APPS,

            /** 重新下载（包损坏或缺失）。 */
            REDOWNLOAD,

            /** 联系开发者/换包（签名不一致）。 */
            CONTACT_DEVELOPER,

            /** 无需动作，仅提示。 */
            NONE,
        }
    }

    /**
     * 校验一个待安装 APK 是否可以覆盖安装当前应用。
     *
     * @param apkPath 待安装 APK 的绝对路径
     * @param expectedPackageName 期望的包名（通常是本 App）
     */
    fun check(
        apkPath: String,
        expectedPackageName: String = context.packageName,
    ): Result {
        val apk = ApkInspector.inspectApk(context, apkPath)
        val installed = ApkInspector.inspectInstalled(context, expectedPackageName)
        val facts =
            InstallFacts(
                expectedPackageName = expectedPackageName,
                incomingPackageName = apk.packageName,
                apkExists = apk.exists,
                apkSizeBytes = apk.sizeBytes,
                installedSignatureHex = installed?.signatureHex,
                incomingSignatureHex = apk.signatureHex,
                installedVersionCode = installed?.versionCode ?: 0L,
                incomingVersionCode = apk.versionCode,
                canRequestPackageInstalls = canRequestPackageInstalls(),
            )
        return toResult(InstallPreconditionChecker.check(facts))
    }

    /** 把领域结论映射为带动作的结论。 */
    private fun toResult(precondition: InstallPrecondition): Result =
        when (precondition) {
            is InstallPrecondition.Allowed -> Result.Allowed(precondition.message)
            is InstallPrecondition.Rejected ->
                Result.Rejected(
                    code = precondition.code,
                    message = precondition.message,
                    action =
                        when (precondition.code) {
                            InstallPrecondition.Code.UNKNOWN_APPS_PERMISSION -> Result.Action.GRANT_UNKNOWN_APPS
                            InstallPrecondition.Code.APK_MISSING,
                            InstallPrecondition.Code.SIGNATURE_UNREADABLE,
                            -> Result.Action.REDOWNLOAD

                            InstallPrecondition.Code.SIGNATURE_MISMATCH,
                            InstallPrecondition.Code.PACKAGE_MISMATCH,
                            -> Result.Action.CONTACT_DEVELOPER

                            else -> Result.Action.NONE
                        },
                )
        }

    /** 是否已获得「安装未知应用」权限（Android 8.0+）。 */
    fun canRequestPackageInstalls(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** 跳转「安装未知应用」授权页（Android 8.0+）。 */
    fun unknownAppsSettingsIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val uri = Uri.parse("package:${context.packageName}")
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /** 生成安装 Intent（通过 FileProvider 授权，系统安装器读取）。 */
    fun installIntent(
        apkPath: String,
        authority: String = "${context.packageName}.fileprovider",
    ): Intent {
        val file = File(apkPath)
        val uri = FileProvider.getUriForFile(context, authority, file)
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
