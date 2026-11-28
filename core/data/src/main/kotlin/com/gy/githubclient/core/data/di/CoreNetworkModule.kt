/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.data.di

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.data.network.GitHubHttpClientFactory
import com.gy.githubclient.core.data.network.NetworkConfig
import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.core.mutation.AuditSink
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** core 统一网络层 DI。HttpClient 用 qualifier 区分，避免与 auth 认证 client 冲突。 */
val coreNetworkModule =
    module {
        single(named(API_HTTP_CLIENT)) {
            // 统一工厂（T3）：expectSuccess / 超时 / JSON 只在这里定义一次，不再各模块自建。
            GitHubHttpClientFactory.create(NetworkConfig(installJson = true))
        }

        single<GitHubApiClient> {
            GitHubApiClient(
                httpClient = get(named(API_HTTP_CLIENT)),
                tokenProvider = get<AuthTokenProvider>(),
                // 审计 sink 由组合根（app 层）提供；core:data 不反向依赖 core:audit。
                // 这里必须是**必需注入**：原先写 `getOrNull<AuditSink>() ?: NoOpAuditSink`，
                // 让"审计模块没装配"退化成"写操作照常成功、审计表永远为空"，既不报错也不打日志
                // （H2：审计形同虚设且无人发现）。改成 get() 后，缺 RoomAuditSink 的装配会在
                // 应用启动时直接抛 NoDefinitionFoundException——问题暴露在第一次启动，
                // 而不是三个月后有人打开审计页才发现是空的。
                auditSink = get<AuditSink>(),
            )
        }
    }

const val API_HTTP_CLIENT = "apiHttpClient"
