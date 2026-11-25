/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.data.translation

import com.gy.githubclient.core.data.network.GitHubJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.content.TextContent
import io.ktor.http.encodeURLParameter
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** 所有免费引擎都失败时抛出，由仓库层翻译成 ApiError.Network。 */
class TranslationUnavailableException(
    message: String,
) : Exception(message)

/**
 * 免费翻译引擎接力（githup 多引擎策略的最小实现）：
 * 有道（国内直连最稳）→ MyMemory（全球可达）→ Google（老牌免费接口）。
 * 任一引擎返回非空译文即成功；全部失败才向上抛错。
 *
 * 隐私：不附加任何 GitHub Token；但被翻译的 README 文本会发送给所选服务商。
 */
class ReadmeTranslationApi(
    private val client: HttpClient,
) {
    suspend fun translate(
        text: String,
        targetLanguage: String,
    ): String {
        val errors = mutableListOf<String>()
        ENGINES.forEach { engine ->
            val translated =
                runCatching { engine(text, targetLanguage) }
                    .onFailure { errors += "${it::class.simpleName}: ${it.message.orEmpty()}" }
                    .getOrNull()
            if (!translated.isNullOrBlank()) return translated
        }
        throw TranslationUnavailableException("翻译服务暂不可用：${errors.joinToString("; ").take(ERROR_HINT_CHARS)}")
    }

    private suspend fun youdao(
        text: String,
        targetLanguage: String,
    ): String {
        val to = if (targetLanguage.startsWith("zh")) "zh-CHS" else "en"
        val response: HttpResponse =
            client.post(YOUDAO_URL) {
                setBody(
                    TextContent(
                        "q=${text.encodeURLParameter()}&from=auto&to=$to",
                        ContentType.Application.FormUrlEncoded,
                    ),
                )
            }
        if (!response.status.isSuccess()) throw TranslationUnavailableException("youdao HTTP ${response.status.value}")
        val dto = GitHubJson.decode(YoudaoResponse.serializer(), response.bodyAsText())
        if (dto.errorCode.isNotBlank() && dto.errorCode != "0") {
            throw TranslationUnavailableException("youdao errorCode=${dto.errorCode}")
        }
        return dto.translation.joinToString("\n").trim()
    }

    private suspend fun myMemory(
        text: String,
        targetLanguage: String,
    ): String {
        val pair = if (targetLanguage.startsWith("zh")) "en|zh-CN" else "zh-CN|en"
        val url = "$MYMEMORY_URL?q=${text.encodeURLParameter()}&langpair=${pair.encodeURLParameter()}"
        val response: HttpResponse = client.get(url)
        if (!response.status.isSuccess()) throw TranslationUnavailableException("mymemory HTTP ${response.status.value}")
        val dto = GitHubJson.decode(MyMemoryResponse.serializer(), response.bodyAsText())
        val translated = dto.responseData.translatedText
        if (dto.responseStatus != 200 || translated.contains("MYMEMORY WARNING", ignoreCase = true)) {
            throw TranslationUnavailableException("mymemory status=${dto.responseStatus}")
        }
        return translated.trim()
    }

    private suspend fun google(
        text: String,
        targetLanguage: String,
    ): String {
        val url =
            "$GOOGLE_URL?client=gtx&sl=auto&tl=$targetLanguage&dt=t&q=${text.encodeURLParameter()}"
        val response: HttpResponse = client.get(url)
        if (!response.status.isSuccess()) throw TranslationUnavailableException("google HTTP ${response.status.value}")
        val root = GitHubJson.instance.parseToJsonElement(response.bodyAsText())
        val segments = root.jsonArray.getOrNull(0) as? JsonArray ?: return ""
        return segments
            .mapNotNull { item ->
                (item as? JsonArray)
                    ?.getOrNull(0)
                    ?.jsonPrimitive
                    ?.content
                    ?.takeIf { it.isNotBlank() }
            }.joinToString("")
            .trim()
    }

    @Serializable
    private data class YoudaoResponse(
        val translation: List<String> = emptyList(),
        @SerialName("errorCode") val errorCode: String = "",
    )

    @Serializable
    private data class MyMemoryResponse(
        @SerialName("responseData") val responseData: MyMemoryData = MyMemoryData(),
        @SerialName("responseStatus") val responseStatus: Int = 0,
    )

    @Serializable
    private data class MyMemoryData(
        @SerialName("translatedText") val translatedText: String = "",
    )

    private companion object {
        const val YOUDAO_URL = "https://aidemo.youdao.com/trans"
        const val MYMEMORY_URL = "https://api.mymemory.translated.net/get"
        const val GOOGLE_URL = "https://translate.googleapis.com/translate_a/single"
        const val ERROR_HINT_CHARS = 240

        /** 顺序即优先级：国内网络下有道最稳，MyMemory 兜底，Google 最后。 */
        val ENGINES: List<suspend ReadmeTranslationApi.(String, String) -> String> =
            listOf(
                { text, target -> youdao(text, target) },
                { text, target -> myMemory(text, target) },
                { text, target -> google(text, target) },
            )
    }
}
