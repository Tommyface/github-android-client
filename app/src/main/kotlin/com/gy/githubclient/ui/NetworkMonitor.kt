/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.ui

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.platform.LocalContext

/**
 * 网络状态监听（P-200 缺口 3：离线态）。
 *
 * 只关心"当前有没有可用网络"，不区分 Wi-Fi / 蜂窝——对 UI 提示而言足够，
 * 且避免为提示引入不必要的权限（`ACCESS_NETWORK_STATE` 已在清单中）。
 */
object NetworkMonitor {
    /** 当前是否有可用网络。 */
    fun isOnline(context: Context): Boolean {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true // 拿不到就按在线处理，不给用户假警报
        val network = manager.activeNetwork ?: return false
        val capabilities = manager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }
}

/**
 * Compose 侧的网络状态（跟随系统网络回调变化）。
 *
 * 注意：**不做"断网就清空界面"**——Room 缓存仍然可用，只是数据可能是旧的，
 * 所以 UI 表现是"顶部提示条：离线模式，显示缓存"，而不是错误页。
 */
@Composable
fun rememberOnlineState(): State<Boolean> {
    val context = LocalContext.current
    return produceState(initialValue = NetworkMonitor.isOnline(context)) {
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (manager == null) {
            value = true
            return@produceState
        }
        val callback =
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    value = true
                }

                override fun onLost(network: Network) {
                    value = NetworkMonitor.isOnline(context)
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities,
                ) {
                    value = NetworkMonitor.isOnline(context)
                }
            }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        awaitDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
}

/** 兼容旧调用点：不需要 Compose 时用这个可变状态。 */
internal fun onlineStateOf(context: Context) = mutableStateOf(NetworkMonitor.isOnline(context))
