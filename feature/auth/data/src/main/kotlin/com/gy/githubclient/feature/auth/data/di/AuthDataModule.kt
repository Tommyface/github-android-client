/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.data.di

import com.gy.githubclient.core.domain.network.AuthTokenProvider
import com.gy.githubclient.feature.auth.data.AuthRepositoryImpl
import com.gy.githubclient.feature.auth.data.AuthTokenProviderImpl
import com.gy.githubclient.feature.auth.data.OAuthDeviceFlowRepositoryImpl
import com.gy.githubclient.feature.auth.data.local.SecureTokenStore
import com.gy.githubclient.feature.auth.data.remote.GitHubAuthApi
import com.gy.githubclient.feature.auth.data.remote.GitHubOAuthApi
import com.gy.githubclient.feature.auth.domain.repository.AuthRepository
import com.gy.githubclient.feature.auth.domain.repository.OAuthDeviceFlowRepository
import com.gy.githubclient.feature.auth.domain.usecase.BindNotificationTokenUseCase
import com.gy.githubclient.feature.auth.domain.usecase.GetCurrentSessionUseCase
import com.gy.githubclient.feature.auth.domain.usecase.ObserveAuthStateUseCase
import com.gy.githubclient.feature.auth.domain.usecase.PollDeviceTokenUseCase
import com.gy.githubclient.feature.auth.domain.usecase.RestoreSessionUseCase
import com.gy.githubclient.feature.auth.domain.usecase.SignInWithOAuthUseCase
import com.gy.githubclient.feature.auth.domain.usecase.SignInWithPatUseCase
import com.gy.githubclient.feature.auth.domain.usecase.SignOutUseCase
import com.gy.githubclient.feature.auth.domain.usecase.StartDeviceFlowUseCase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module

/** auth data 层 DI：安全存储 + 远程 API + 仓库 + 用例。 */
val authDataModule =
    module {
        single { SecureTokenStore(androidContext()) }

        // 认证专用 client：不注入 token（token 按调用显式传入以校验），仅统一 JSON 解析。
        single {
            HttpClient(OkHttp) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            isLenient = true
                        },
                    )
                }
                // 国内直连 api.github.com 可能很慢或无响应，显式超时避免登录请求长期挂起。
                install(HttpTimeout) {
                    connectTimeoutMillis = 15_000
                    socketTimeoutMillis = 30_000
                    requestTimeoutMillis = 30_000
                }
            }
        }

        single { GitHubAuthApi(get<HttpClient>()) }

        single { GitHubOAuthApi(get<HttpClient>()) }

        single<AuthRepository> {
            AuthRepositoryImpl(
                tokenStore = get(),
                api = get(),
            )
        }

        // client_id 由 app 模块 BuildConfig 经 Koin properties 注入；缺省兜底保证单测可构造。
        single<OAuthDeviceFlowRepository> {
            OAuthDeviceFlowRepositoryImpl(
                api = get(),
                clientId = getProperty("oauth.clientId", GitHubOAuthApi.DEFAULT_CLIENT_ID),
            )
        }

        // 依赖倒置：向 core 网络层提供 token 来源（主/通知双通道）。
        single<AuthTokenProvider> { AuthTokenProviderImpl(get<AuthRepository>()) }

        factory { SignInWithPatUseCase(get<AuthRepository>()) }
        factory { SignInWithOAuthUseCase(get<AuthRepository>()) }
        factory { BindNotificationTokenUseCase(get<AuthRepository>()) }
        factory { ObserveAuthStateUseCase(get<AuthRepository>()) }
        factory { RestoreSessionUseCase(get<AuthRepository>()) }
        factory { GetCurrentSessionUseCase(get<AuthRepository>()) }
        factory { SignOutUseCase(get<AuthRepository>()) }
        factory { StartDeviceFlowUseCase(get<OAuthDeviceFlowRepository>()) }
        factory { PollDeviceTokenUseCase(get<OAuthDeviceFlowRepository>()) }
    }
