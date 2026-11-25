/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.domain.translation

import com.gy.githubclient.core.domain.network.NetworkResult

/**
 * README 翻译契约（githup「翻译本页」的 Native 等价能力）。
 *
 * 说明：翻译文本会发送给第三方免费翻译服务商 —— 这是翻译的前提，UI 必须明确告知。
 * 只翻译 README 中的自然语言段落，不碰代码块、用户名、版本号与 URL。
 */
interface TranslationRepository {
    /** 翻译单段文本；命中本地缓存时不发网络请求。[targetLanguage] 使用 BCP-47（如 zh-CN）。 */
    suspend fun translate(
        text: String,
        targetLanguage: String = DEFAULT_TARGET,
    ): NetworkResult<String>

    /** 清空本地译文缓存（设置页「清除缓存」）。 */
    suspend fun clearCache()

    companion object {
        const val DEFAULT_TARGET = "zh-CN"
    }
}

/** 翻译单段文本（用例层薄封装，便于 ViewModel 依赖倒置）。 */
class TranslateTextUseCase(
    private val repository: TranslationRepository,
) {
    suspend operator fun invoke(
        text: String,
        targetLanguage: String = TranslationRepository.DEFAULT_TARGET,
    ): NetworkResult<String> = repository.translate(text, targetLanguage)
}

/** 清空译文缓存。 */
class ClearTranslationCacheUseCase(
    private val repository: TranslationRepository,
) {
    suspend operator fun invoke() = repository.clearCache()
}

/**
 * 判断一段 README 文本是否值得翻译（纯函数，可单测）：
 * - 跳过纯 URL 行、版本号、纯标识符/路径、纯中文、命令/代码片段；
 * - 正文里夹带链接/徽章可以翻，但去掉 URL 后仍需至少两个词，避免只翻出空壳；
 * - 标题允许单个单词（Installation），正文至少两个词；
 * - 保守优先：宁可不翻，也不要把 `npm install` 翻成一句中文。
 */
object TranslationEligibility {
    private val URL = Regex("""https?://\S+|www\.\S+""", RegexOption.IGNORE_CASE)
    private val VERSION = Regex("""^v?\d+(\.\d+)*([-+].*)?$""", RegexOption.IGNORE_CASE)
    private val COMMAND = Regex("""^(\$|>)\s|^(npm|yarn|pnpm|pip|gradle|\./gradlew|git|cd|curl|brew|apt|docker)\b""")

    /** 强代码标记：出现即视为代码片段（README 正文极少自然出现花括号/分号/箭头）。 */
    private val CODE_MARKERS = Regex("""[{};]|=>|::|->""")
    private val CODE_SYMBOLS = setOf('{', '}', '=', ';', '<', '>', '|', '\\', '$', '`')

    private const val MIN_LENGTH = 2
    private const val MAX_LENGTH = 5_000
    private const val LATIN_MIN = 2
    private const val CODE_RATIO_DENOMINATOR = 8

    fun shouldTranslate(
        text: String,
        isHeading: Boolean = false,
    ): Boolean {
        val value = text.trim()
        if (value.length !in MIN_LENGTH..MAX_LENGTH) return false
        // 标题含 URL 基本是导航/徽章行，跳过；正文允许夹带链接。
        if (isHeading && URL.containsMatchIn(value)) return false
        if (isNonProse(value)) return false
        if (!hasEnoughLatin(value)) return false
        if (isCodeHeavy(value)) return false
        return hasEnoughWords(value, isHeading)
    }

    /** 版本号、命令与强代码标记：不应进入翻译。 */
    private fun isNonProse(value: String): Boolean =
        VERSION.matches(value) || COMMAND.containsMatchIn(value) || CODE_MARKERS.containsMatchIn(value)

    /** 至少两个拉丁字母，且中文占比不过半（纯中文无需翻译）。 */
    private fun hasEnoughLatin(value: String): Boolean {
        val letters = value.count { it.isLetter() }
        if (letters == 0) return false
        val han = value.count { it.isHan() }
        if (han * 3 >= letters) return false
        return value.count { it in 'a'..'z' || it in 'A'..'Z' } >= LATIN_MIN
    }

    /** 代码符号密度过高视为代码/表格片段。 */
    private fun isCodeHeavy(value: String): Boolean {
        val codeSymbols = value.count { it in CODE_SYMBOLS }
        return codeSymbols * CODE_RATIO_DENOMINATOR > value.length
    }

    /** 去掉 URL 后统计有效词：正文至少两个词，单单词标识符/路径不翻。 */
    private fun hasEnoughWords(
        value: String,
        isHeading: Boolean,
    ): Boolean {
        val words =
            URL
                .replace(value, " ")
                .split(Regex("""\s+"""))
                .filter { word -> word.any { it.isLetter() } }
        if (!isHeading && words.size < 2) return false
        if (words.size == 1 && value.any { it.isSeparator() }) return false
        return !(value == value.uppercase() && value.length <= 8)
    }

    private fun Char.isSeparator(): Boolean = this == '/' || this == '_' || this == '.'

    private fun Char.isHan(): Boolean =
        this in '\u4E00'..'\u9FFF' || this in '\u3400'..'\u4DBF' || this in '\uF900'..'\uFAFF'
}
