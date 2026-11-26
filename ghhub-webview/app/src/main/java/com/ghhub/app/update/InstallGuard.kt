package com.ghhub.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File
import java.security.MessageDigest

/**
 * 覆盖安装守卫（cover install guard）。
 *
 * 覆盖安装要成立需同时满足三点：
 *  1) 新包签名与已安装包签名一致（Android 强制，签名不同一律 INSTALL_FAILED_UPDATE_INCOMPATIBLE）；
 *  2) 新包 versionCode >= 已安装 versionCode（否则 INSTALL_FAILED_VERSION_DOWNGRADE）；
 *  3) Android 8.0+ 需授予「安装未知应用」权限（REQUEST_INSTALL_PACKAGES 仅声明还不够）。
 *
 * 本类只做「前置校验 + 引导授权 + 产出安装 Intent」，实际安装仍交系统安装器 / AppUpdater，
 * 不重写轮子，符合方案「不重复造轮子」原则。
 */
object InstallGuard {

    data class CheckResult(
        val allowed: Boolean,
        val code: String,
        val message: String,
        val needUnknownAppsPermission: Boolean = false
    )

    /** 是否已获得「安装未知应用」授权（API 26+ 需动态判定）。 */
    fun canInstallUnknownApps(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /** 跳转到「安装未知应用」授权设置页（API 26+）。 */
    fun unknownAppsSettingsIntent(context: Context): Intent {
        val uri = Uri.parse("package:${context.packageName}")
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * 覆盖安装综合前置校验：文件 → 签名 SHA-256 → versionCode → 未知来源权限。
     * 返回 allowed=false 时，调用方应展示 message 并中止安装；
     * needUnknownAppsPermission=true 时应先跳转授权页再重试。
     */
    fun check(context: Context, apkPath: String): CheckResult {
        val apk = File(apkPath)
        if (!apk.exists() || apk.length() <= 0) {
            return CheckResult(false, "APK_NOT_FOUND", "安装包不存在或为空，请重新下载")
        }

        val pm = context.packageManager
        val incoming = pm.getPackageArchiveInfo(apkPath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: return CheckResult(false, "APK_PARSE_FAILED", "安装包解析失败，文件可能损坏")

        // 目标包名：本应用（覆盖安装自身）；校验待装包是否就是本应用。
        val targetPkg = context.packageName
        if (incoming.packageName != targetPkg) {
            return CheckResult(
                false, "PACKAGE_MISMATCH",
                "安装包包名(${incoming.packageName})与本应用($targetPkg)不一致，不可覆盖安装"
            )
        }

        // 已安装版本签名
        val installed = try {
            pm.getPackageInfo(targetPkg, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }

        if (installed != null) {
            val installedSig = firstSignatureSha256(installed)
            val incomingSig = firstSignatureSha256(incoming)
            if (installedSig == null || incomingSig == null) {
                return CheckResult(false, "SIGNATURE_UNREADABLE", "无法读取安装包签名，已中止以防覆盖失败")
            }
            if (!installedSig.equals(incomingSig, ignoreCase = true)) {
                return CheckResult(
                    false, "SIGNATURE_MISMATCH",
                    "签名不一致（本机 $installedSig / 新包 $incomingSig）。覆盖安装必须同签名，拒绝安装以防数据丢失"
                )
            }
            val installedCode = versionCode(installed)
            val incomingCode = versionCode(incoming)
            if (incomingCode < installedCode) {
                return CheckResult(
                    false, "DOWNGRADE_BLOCKED",
                    "新版本(${incomingCode})低于已安装版本(${installedCode})，系统禁止降级覆盖安装"
                )
            }
        }

        // 权限最后判定：有签名/版本问题时不打扰用户，权限问题单列以便引导
        if (!canInstallUnknownApps(context)) {
            return CheckResult(
                false, "UNKNOWN_APPS_PERMISSION",
                "需授予「允许安装未知应用」权限后才能覆盖安装",
                needUnknownAppsPermission = true
            )
        }

        return CheckResult(true, "OK", "校验通过，可覆盖安装（保留应用数据）")
    }

    @Suppress("DEPRECATION")
    private fun firstSignatureSha256(info: PackageInfo): String? {
        val signatures: Array<Signature>? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.signingCertificateHistory
        } else {
            info.signatures
        }
        val first = signatures?.firstOrNull() ?: return null
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(first.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    @Suppress("DEPRECATION")
    private fun versionCode(info: PackageInfo): Long {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
    }
}
