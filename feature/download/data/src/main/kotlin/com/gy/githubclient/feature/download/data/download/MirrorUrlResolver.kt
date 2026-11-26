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
    ): List<String> = candidateChain(githubUrl, preference, catalog, latencies).map { it.second }

    /**
     * 候选链 + 镜像 id（M1-05：健康度与熔断需要知道每个候选属于哪个源）。
     *
     * id 为 null 表示「直连」或「自定义模板」——它们不属于内置池，不参与熔断统计。
     */
    fun candidateChain(
        githubUrl: String,
        preference: MirrorPreference,
        catalog: List<MirrorConfig>,
        latencies: Map<String, Int>,
        openCircuitIds: Set<String> = emptySet(),
    ): List<Pair<String?, String>> {
        if (!MirrorRewriter.shouldRewrite(githubUrl)) return listOf(null to githubUrl)
        val kind = MirrorRewriter.classify(githubUrl) ?: TrafficKind.RELEASE_ASSET

        // 直连偏好：不产生镜像候选，仅直连。
        if (preference is MirrorPreference.Direct) return listOf(null to githubUrl)

        val primaryId = (preference as? MirrorPreference.Selected)?.id
        val primaryTemplate =
            when (preference) {
                is MirrorPreference.Custom -> preference.template.takeIf { it.isNotBlank() }
                is MirrorPreference.Selected ->
                    catalog.firstOrNull { it.id == preference.id }?.urlTemplate
                MirrorPreference.Direct -> null
            }
        // 处于熔断冷却期的源不进入候选链（M1-05）。
        val primary =
            primaryTemplate
                ?.takeIf { primaryId == null || primaryId !in openCircuitIds }
                ?.let { MirrorRewriter.applyTemplate(it, githubUrl) }
        val fallbacks =
            catalog
                .filter {
                    kind in it.trafficKinds &&
                        it.id != primaryId &&
                        it.urlTemplate != primaryTemplate &&
                        it.id !in openCircuitIds
                }
                .sortedBy { latencies[it.id] ?: Int.MAX_VALUE }
                .mapNotNull { mirror ->
                    mirror.urlTemplate?.let { template -> mirror.id to MirrorRewriter.applyTemplate(template, githubUrl) }
                }
        // 自定义模板没有内置 id（null），但仍要作为首选候选。
        val primaryEntry = primary?.let { url -> primaryId to url }
        val chain = (listOfNotNull(primaryEntry) + fallbacks + (null to githubUrl))
        return chain.distinctBy { it.second }
    }
}
