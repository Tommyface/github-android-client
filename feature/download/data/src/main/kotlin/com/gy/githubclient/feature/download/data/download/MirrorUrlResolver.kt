/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.data.download

import com.gy.githubclient.core.data.network.MirrorRewriter
import com.gy.githubclient.core.domain.model.mirror.MirrorConfig
import com.gy.githubclient.core.domain.model.mirror.MirrorPreference
import com.gy.githubclient.core.domain.model.mirror.TrafficKind
import com.gy.githubclient.core.domain.model.mirror.fastestReleaseMirror

/**
 * 镜像 URL 决策器（纯逻辑，可 JVM 单测）：
 * 根据偏好把 GitHub 直连地址解析为镜像地址；直连偏好或不支持的流量类型返回 null。
 */
object MirrorUrlResolver {
    fun resolve(
        githubUrl: String,
        preference: MirrorPreference,
        catalog: List<MirrorConfig>,
    ): String? {
        if (!MirrorRewriter.shouldRewrite(githubUrl)) return null
        val template =
            when (preference) {
                MirrorPreference.Direct -> return null
                is MirrorPreference.Custom -> preference.template.takeIf { it.isNotBlank() }
                is MirrorPreference.Selected ->
                    catalog.firstOrNull { it.id == preference.id }?.urlTemplate
            } ?: return null

        // 选定镜像不支持该流量类型（如 jsdelivr 仅 raw）时不改写。
        val kind = MirrorRewriter.classify(githubUrl) ?: TrafficKind.RELEASE_ASSET
        val mirror =
            when (preference) {
                is MirrorPreference.Selected -> catalog.firstOrNull { it.id == preference.id }
                else -> null
            }
        if (mirror != null && kind !in mirror.trafficKinds) return null

        return MirrorRewriter.applyTemplate(template, githubUrl)
    }

    /** 自动模式：按延迟选最快 RELEASE_ASSET 镜像（供将来「自动选择」偏好使用）。 */
    fun fastestTemplate(
        catalog: List<MirrorConfig>,
        latencies: Map<String, Int>,
    ): String? = fastestReleaseMirror(catalog, latencies)?.first?.urlTemplate

    /**
     * 失败轮换候选链（v0.5.3 M1）：
     * 选定/自定义镜像优先 → 池内其余支持该流量的镜像按延迟升序 → 直连兜底。
     * - 用户明确选「直连」偏好时不偷用镜像（尊重偏好与隐私预期）；
     * - 自定义模板失败后允许落入内置镜像池兜底；
     * - 候选去重并保持顺序。
     */
    fun candidateUrls(
        githubUrl: String,
        preference: MirrorPreference,
        catalog: List<MirrorConfig>,
        latencies: Map<String, Int>,
    ): List<String> {
        if (!MirrorRewriter.shouldRewrite(githubUrl)) return listOf(githubUrl)
        val kind = MirrorRewriter.classify(githubUrl) ?: TrafficKind.RELEASE_ASSET
        val primaryTemplate =
            when (preference) {
                // 直连偏好：不产生镜像候选，仅直连。
                MirrorPreference.Direct -> return listOf(githubUrl)
                is MirrorPreference.Custom -> preference.template.takeIf { it.isNotBlank() }
                is MirrorPreference.Selected ->
                    catalog.firstOrNull { it.id == preference.id }?.urlTemplate
            }
        val primary = primaryTemplate?.let { MirrorRewriter.applyTemplate(it, githubUrl) }
        val primaryId =
            when (preference) {
                is MirrorPreference.Selected -> preference.id
                else -> null
            }
        val fallbacks =
            catalog
                .filter { kind in it.trafficKinds && it.id != primaryId && it.urlTemplate != primaryTemplate }
                .sortedBy { latencies[it.id] ?: Int.MAX_VALUE }
                .mapNotNull { mirror -> mirror.urlTemplate?.let { MirrorRewriter.applyTemplate(it, githubUrl) } }
        return (listOfNotNull(primary) + fallbacks + githubUrl).distinct()
    }
}
