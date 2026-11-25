/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data.translation

import com.gy.githubclient.core.domain.network.ApiError
import com.gy.githubclient.core.domain.network.NetworkResult
import com.gy.githubclient.feature.repo.domain.translation.TranslationRepository
import kotlinx.coroutines.CancellationException

/**
 * README 翻译仓库：本地缓存优先 → 免费引擎接力 → 统一错误映射。
 *
 * - 命中缓存时零网络请求，重复浏览同一 README 不再消耗翻译额度；
 * - 译文按「目标语言 + 原文」隔离，切换目标语言不会串味；
 * - 取消（页面退出）原样上抛，不做失败包装，避免把协程取消当成网络错误。
 */
class TranslationRepositoryImpl(
    private val api: ReadmeTranslationApi,
    private val cache: TranslationCacheStore,
) : TranslationRepository {
    @Suppress("TooGenericExceptionCaught")
    override suspend fun translate(
        text: String,
        targetLanguage: String,
    ): NetworkResult<String> {
        val source = text.trim()
        if (source.isEmpty()) return NetworkResult.Success("")

        cache.get(source, targetLanguage)?.let { cached ->
            return NetworkResult.Success(cached)
        }

        return try {
            val translated = api.translate(source, targetLanguage)
            if (translated.isBlank()) {
                NetworkResult.Failure(ApiError.Unknown("翻译结果为空"))
            } else {
                cache.put(source, targetLanguage, translated)
                NetworkResult.Success(translated)
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (unavailable: TranslationUnavailableException) {
            NetworkResult.Failure(ApiError.Network(unavailable.message ?: "翻译服务不可用"))
        } catch (error: Exception) {
            NetworkResult.Failure(
                ApiError.Network(error.message ?: error::class.simpleName ?: "翻译失败"),
            )
        }
    }

    override suspend fun clearCache() {
        cache.clear()
    }
}
