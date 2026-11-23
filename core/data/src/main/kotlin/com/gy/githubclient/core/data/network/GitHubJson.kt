/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.data.network

import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json

/**
 * 全项目共享 JSON 解析入口。
 *
 * 背景：Ktor 3.0.3 的 ContentNegotiation 管线在 Android 真机（OkHttp 引擎，body 为
 * SourceByteReadChannel）上存在反序列化失效问题（NoTransformationFoundException），
 * 而 JVM 单测（MockEngine / MockWebServer）无法复现。因此所有响应体统一改为
 * `bodyAsText()` + 编译期生成的显式 serializer 解析，完全绕开 Ktor 转换管线与
 * 运行时 KType 反射查找，保证真机可靠性。
 */
object GitHubJson {
    val instance: Json =
        Json {
            ignoreUnknownKeys = true
            isLenient = true
        }

    /** 用编译期生成的 serializer 解析文本，不依赖运行时 KType 反射。 */
    fun <T> decode(
        serializer: KSerializer<T>,
        text: String,
    ): T = instance.decodeFromString(serializer, text)
}
