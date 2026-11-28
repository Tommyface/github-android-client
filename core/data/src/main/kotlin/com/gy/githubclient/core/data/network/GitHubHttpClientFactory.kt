/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.data.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json

/** GitHub REST 基址的唯一真源；app / feature 不得再写字面量。 */
object GitHubEndpoints {
    const val API_BASE_URL: String = "https://api.github.com"
}

/**
 * HttpClient 配置。
 *
 * 差异只允许出现在「超时数值」与「是否装 JSON 插件」上，其余（`expectSuccess = false`）
 * 必须一致——同一项目里对非 2xx 的处理方式不该按模块改变。
 *
 * 超时为 `null` 表示**不设置该项**（Ktor 默认无限），而不是「用默认值」。下载测速链路
 * 依赖这一点：`MirrorLatencyProbeImpl` 用 per-request `timeout {}` 自己设超时，
 * 若 client 上再挂一个 30s `requestTimeout`，大文件下载与慢镜像探测会被提前掐断。
 */
data class NetworkConfig(
    val connectTimeoutMillis: Long? = 15_000,
    val socketTimeoutMillis: Long? = 30_000,
    val requestTimeoutMillis: Long? = 30_000,
    /**
     * 是否安装 ContentNegotiation。本项目实际解析统一走 `bodyAsText()` + 显式 serializer，
     * 只有沿用了旧配置的 core/auth 两处仍挂着它（保持现状，避免顺手改变行为）。
     */
    val installJson: Boolean = false,
) {
    internal val hasAnyTimeout: Boolean
        get() =
            connectTimeoutMillis != null ||
                socketTimeoutMillis != null ||
                requestTimeoutMillis != null
}

/**
 * 全 App 唯一的 HttpClient 工厂（T3）。
 *
 * 改造前有 5 处各自 `HttpClient(OkHttp) { … }`：core 的 API client、auth 的认证 client、
 * download 的镜像测速 client、更新检查的 2 个临时 client。同一件事被写了 5 遍，
 * 于是任何全局策略（超时、代理、证书、日志、限流退避）都要改 5 个地方，漏一处就是
 * 行为不一致——例如更新检查就从未有过限流退避与 401 回调。
 */
object GitHubHttpClientFactory {
    fun create(config: NetworkConfig = NetworkConfig()): HttpClient =
        HttpClient(OkHttp) {
            // 项目约定：非 2xx 不抛异常，统一交给 ApiErrorMapper / MutationErrorMapper 分类。
            expectSuccess = false
            if (config.installJson) {
                install(ContentNegotiation) { json(GitHubJson.instance) }
            }
            if (config.hasAnyTimeout) {
                install(HttpTimeout) {
                    config.connectTimeoutMillis?.let { connectTimeoutMillis = it }
                    config.socketTimeoutMillis?.let { socketTimeoutMillis = it }
                    config.requestTimeoutMillis?.let { requestTimeoutMillis = it }
                }
            }
        }
}
