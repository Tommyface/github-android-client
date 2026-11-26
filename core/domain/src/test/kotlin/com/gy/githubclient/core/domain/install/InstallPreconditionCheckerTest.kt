/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** M1-08：安装前置校验（纯逻辑）。 */
class InstallPreconditionCheckerTest {
    /** 测试用例参数（默认值代表"一切正常"）。 */
    private data class Case(
        val incomingPackage: String? = "com.gy.githubclient",
        val apkExists: Boolean = true,
        val apkSize: Long = 1024L,
        val installedSig: String? = "AA",
        val incomingSig: String? = "AA",
        val installedCode: Long = 20L,
        val incomingCode: Long = 21L,
        val canInstall: Boolean = true,
    )

    private fun check(case: Case = Case()): InstallPrecondition =
        InstallPreconditionChecker.check(
            InstallFacts(
                expectedPackageName = "com.gy.githubclient",
                incomingPackageName = case.incomingPackage,
                apkExists = case.apkExists,
                apkSizeBytes = case.apkSize,
                installedSignatureHex = case.installedSig,
                incomingSignatureHex = case.incomingSig,
                installedVersionCode = case.installedCode,
                incomingVersionCode = case.incomingCode,
                canRequestPackageInstalls = case.canInstall,
            ),
        )

    @Test
    fun allowsValidUpgrade() {
        assertTrue(check() is InstallPrecondition.Allowed)
    }

    @Test
    fun rejectsMissingApk() {
        val result = check(Case(apkExists = false))
        assertEquals(InstallPrecondition.Code.APK_MISSING, (result as InstallPrecondition.Rejected).code)
    }

    @Test
    fun rejectsEmptyApk() {
        val result = check(Case(apkSize = 0L))
        assertEquals(InstallPrecondition.Code.APK_MISSING, (result as InstallPrecondition.Rejected).code)
    }

    @Test
    fun rejectsPackageMismatch() {
        val result = check(Case(incomingPackage = "com.other.app"))
        assertEquals(InstallPrecondition.Code.PACKAGE_MISMATCH, (result as InstallPrecondition.Rejected).code)
    }

    @Test
    fun rejectsSignatureMismatch() {
        val result = check(Case(incomingSig = "BB"))
        assertEquals(InstallPrecondition.Code.SIGNATURE_MISMATCH, (result as InstallPrecondition.Rejected).code)
    }

    @Test
    fun signatureComparisonIgnoresCaseAndSeparators() {
        val result = check(Case(installedSig = "aa:bb", incomingSig = "AABB"))
        assertTrue(result is InstallPrecondition.Allowed)
    }

    @Test
    fun rejectsUnreadableSignature() {
        val result = check(Case(incomingSig = null))
        assertEquals(InstallPrecondition.Code.SIGNATURE_UNREADABLE, (result as InstallPrecondition.Rejected).code)
    }

    @Test
    fun rejectsDowngrade() {
        val result = check(Case(incomingCode = 19L))
        assertEquals(InstallPrecondition.Code.DOWNGRADE_BLOCKED, (result as InstallPrecondition.Rejected).code)
    }

    @Test
    fun rejectsWhenUnknownAppsPermissionMissing() {
        val result = check(Case(canInstall = false))
        assertEquals(
            InstallPrecondition.Code.UNKNOWN_APPS_PERMISSION,
            (result as InstallPrecondition.Rejected).code,
        )
    }

    @Test
    fun firstInstallDoesNotRequireSignatureMatch() {
        val result = check(Case(installedSig = null, incomingSig = "BB"))
        assertTrue(result is InstallPrecondition.Allowed)
    }
}
