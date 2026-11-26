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
    private fun check(
        incomingPackage: String? = "com.gy.githubclient",
        apkExists: Boolean = true,
        apkSize: Long = 1024L,
        installedSig: String? = "AA",
        incomingSig: String? = "AA",
        installedCode: Long = 20L,
        incomingCode: Long = 21L,
        canInstall: Boolean = true,
    ): InstallPrecondition =
        InstallPreconditionChecker.check(
            expectedPackageName = "com.gy.githubclient",
            incomingPackageName = incomingPackage,
            apkExists = apkExists,
            apkSizeBytes = apkSize,
            installedSignatureHex = installedSig,
            incomingSignatureHex = incomingSig,
            installedVersionCode = installedCode,
            incomingVersionCode = incomingCode,
            canRequestPackageInstalls = canInstall,
        )

    @Test
    fun allowsValidUpgrade() {
        assertTrue(check() is InstallPrecondition.Allowed)
    }

    @Test
    fun rejectsMissingApk() {
        val result = check(apkExists = false)
        assertEquals(InstallPrecondition.Code.APK_MISSING, (result as InstallPrecondition.Rejected).code)
    }

    @Test
    fun rejectsEmptyApk() {
        val result = check(apkSize = 0L)
        assertEquals(InstallPrecondition.Code.APK_MISSING, (result as InstallPrecondition.Rejected).code)
    }

    @Test
    fun rejectsPackageMismatch() {
        val result = check(incomingPackage = "com.other.app")
        assertEquals(InstallPrecondition.Code.PACKAGE_MISMATCH, (result as InstallPrecondition.Rejected).code)
    }

    @Test
    fun rejectsSignatureMismatch() {
        val result = check(incomingSig = "BB")
        assertEquals(InstallPrecondition.Code.SIGNATURE_MISMATCH, (result as InstallPrecondition.Rejected).code)
    }

    @Test
    fun signatureComparisonIgnoresCaseAndSeparators() {
        val result = check(installedSig = "aa:bb", incomingSig = "AABB")
        assertTrue(result is InstallPrecondition.Allowed)
    }

    @Test
    fun rejectsUnreadableSignature() {
        val result = check(incomingSig = null)
        assertEquals(InstallPrecondition.Code.SIGNATURE_UNREADABLE, (result as InstallPrecondition.Rejected).code)
    }

    @Test
    fun rejectsDowngrade() {
        val result = check(incomingCode = 19L)
        assertEquals(InstallPrecondition.Code.DOWNGRADE_BLOCKED, (result as InstallPrecondition.Rejected).code)
    }

    @Test
    fun rejectsWhenUnknownAppsPermissionMissing() {
        val result = check(canInstall = false)
        assertEquals(
            InstallPrecondition.Code.UNKNOWN_APPS_PERMISSION,
            (result as InstallPrecondition.Rejected).code,
        )
    }

    @Test
    fun firstInstallDoesNotRequireSignatureMatch() {
        val result = check(installedSig = null, incomingSig = "BB")
        assertTrue(result is InstallPrecondition.Allowed)
    }
}
