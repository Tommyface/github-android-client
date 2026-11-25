/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.data.di

import com.gy.githubclient.core.data.network.GitHubApiClient
import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.core.mutation.AuditSink
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.qualifier.named
import org.koin.dsl.module

/** core 统一网络层 DI。HttpClient 用 qualifier 区分，避免与 auth 认证 client 冲突。 */
val coreNetworkModule =
    module {
        single(named(API_HTTP_CLIENT)) {
            HttpClient(OkHttp) {
                // 关闭「非 2xx 抛异常」，统一交给 GitHubApiClient 映射 ApiError。
                expectSuccess = false
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }
                install(HttpTimeout) {
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 30_000
                    requestTimeoutMillis = 30_000
                }
            }
        }

        single<GitHubApiClient> {
            GitHubApiClient(
                httpClient = get(named(API_HTTP_CLIENT)),
                tokenProvider = get<AuthTokenProvider>(),
                auditSink = get<AuditSink>(),
            )
        }
    }

const val API_HTTP_CLIENT = "apiHttpClient"
