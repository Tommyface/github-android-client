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
        // 直连偏好：不产生镜像候选，仅直连。
        if (preference is MirrorPreference.Direct) return listOf(null to githubUrl)

        val kind = MirrorRewriter.classify(githubUrl) ?: TrafficKind.RELEASE_ASSET
        val primaryId = (preference as? MirrorPreference.Selected)?.id
        val primaryTemplate =
            when (preference) {
                is MirrorPreference.Custom -> preference.template.takeIf { it.isNotBlank() }
                is MirrorPreference.Selected -> catalog.firstOrNull { it.id == preference.id }?.urlTemplate
                MirrorPreference.Direct -> null
            }
        // 自定义模板没有内置 id（null），但仍要作为首选候选；熔断中的源不参与（M1-05）。
        val primary =
            primaryTemplate
                ?.takeIf { primaryId == null || primaryId !in openCircuitIds }
                ?.let { MirrorRewriter.applyTemplate(it, githubUrl) }
        val fallbacks =
            fallbackCandidates(
                FallbackQuery(
                    githubUrl = githubUrl,
                    kind = kind,
                    catalog = catalog,
                    latencies = latencies,
                    excludedId = primaryId,
                    excludedTemplate = primaryTemplate,
                    openCircuitIds = openCircuitIds,
                ),
            )
        val primaryEntry = primary?.let { url -> primaryId to url }
        val chain = (listOfNotNull(primaryEntry) + fallbacks + (null to githubUrl))
        return chain.distinctBy { it.second }
    }

    /** 兜底候选：池内其余支持该流量、未被熔断、且不是首选的镜像，按延迟升序。 */
    private fun fallbackCandidates(query: FallbackQuery): List<Pair<String, String>> =
        query.catalog
            .filter {
                query.kind in it.trafficKinds &&
                    it.id != query.excludedId &&
                    it.urlTemplate != query.excludedTemplate &&
                    it.id !in query.openCircuitIds
            }
            .sortedBy { query.latencies[it.id] ?: Int.MAX_VALUE }
            .mapNotNull { mirror ->
                val template = mirror.urlTemplate ?: return@mapNotNull null
                val url = MirrorRewriter.applyTemplate(template, query.githubUrl) ?: return@mapNotNull null
                mirror.id to url
            }

    /** 兜底候选的查询参数（用数据类承载，避免长参数列表）。 */
    private data class FallbackQuery(
        val githubUrl: String,
        val kind: TrafficKind,
        val catalog: List<MirrorConfig>,
        val latencies: Map<String, Int>,
        val excludedId: String?,
        val excludedTemplate: String?,
        val openCircuitIds: Set<String>,
    )
}
