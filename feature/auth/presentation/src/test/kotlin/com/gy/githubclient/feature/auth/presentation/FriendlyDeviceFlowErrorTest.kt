/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.presentation

import org.junit.Assert.assertEquals
import org.junit.Test

/** 回归：Device Flow 网络异常文案中文化映射（v0.5.2）。 */
class FriendlyDeviceFlowErrorTest {
    @Test
    fun timeoutMapsToChineseGuidance() {
        val raw =
            "Socket timeout has expired [url=https://github.com/login/device/code, " +
                "socket_timeout=unknown] ms"
        val text = friendlyDeviceFlowError(raw)
        assertEquals(true, text.contains("网络连接超时"))
        assertEquals(true, text.contains("github.com"))
    }

    @Test
    fun dnsFailureMapsToChineseGuidance() {
        val text = friendlyDeviceFlowError("Unable to resolve host \"github.com\": No address associated")
        assertEquals(true, text.contains("域名解析失败"))
    }

    @Test
    fun connectionResetMapsToChineseGuidance() {
        val text = friendlyDeviceFlowError("unexpected end of stream on https://github.com")
        assertEquals(true, text.contains("网络连接中断"))
    }

    @Test
    fun unknownErrorPassesThroughUnchanged() {
        val raw = "[-3] some opaque failure"
        assertEquals(raw, friendlyDeviceFlowError(raw))
    }
}
