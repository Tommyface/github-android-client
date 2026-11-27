/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.model.mirror

/**
 * 单个镜像的健康画像（M1-05）。
 *
 * @param successCount 累计成功次数
 * @param failureCount 累计失败次数
 * @param consecutiveFailures 当前连续失败次数
 * @param circuitOpenedAt 熔断开启时刻（epoch millis）；null 表示未熔断
 */
data class MirrorHealth(
    val successCount: Int = 0,
    val failureCount: Int = 0,
    val consecutiveFailures: Int = 0,
    val circuitOpenedAt: Long? = null,
) {
    /** 成功率（无任何记录时按 0.7 估计，避免冷启动把新源判死）。 */
    val successRate: Double
        get() {
            val total = successCount + failureCount
            return if (total == 0) DEFAULT_SUCCESS_RATE else successCount.toDouble() / (total + 1)
        }

    /** 健康分：成功率 ÷ 延迟因子（延迟未知时按 1 计）。 */
    fun score(latencyMs: Int?): Double {
        val latencyFactor = ((latencyMs ?: DEFAULT_LATENCY_MS).coerceAtLeast(1)) / 100.0
        return successRate / latencyFactor
    }

    /** 熔断是否处于开启状态（超过冷却时间后自动半开）。 */
    fun isCircuitOpen(
        now: Long,
        cooldownMillis: Long,
    ): Boolean {
        val openedAt = circuitOpenedAt ?: return false
        return now - openedAt < cooldownMillis
    }

    companion object {
        const val DEFAULT_SUCCESS_RATE = 0.7
        const val DEFAULT_LATENCY_MS = 1_000
    }
}

/**
 * 镜像健康登记簿（纯逻辑，可单测）。
 *
 * 职责：
 *  - 记录每个镜像的成功/失败；
 *  - 连续失败达到阈值即**熔断**（打开电路），冷却期内不再选择该源；
 *  - 冷却期满后允许**半开**重试（成功即复位，失败则再次熔断）。
 *
 * 为什么要有：镜像源会"抽风"。没有熔断时每次下载都会先撞同一个坏源，
 * 用户要多等一次超时；而完全移除坏源又会失去"过一会儿它可能就好了"的弹性。
 */
class MirrorHealthRegistry(
    private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
) {
    private val states = mutableMapOf<String, MirrorHealth>()

    /** 当前所有源的健康画像（只读快照）。 */
    fun snapshot(): Map<String, MirrorHealth> = states.toMap()

    /** 某源的健康画像（未记录时返回空画像）。 */
    fun healthOf(id: String): MirrorHealth = states[id] ?: MirrorHealth()

    /** 记录一次成功：清零连续失败，关闭熔断。 */
    fun recordSuccess(id: String) {
        val current = healthOf(id)
        states[id] =
            current.copy(
                successCount = current.successCount + 1,
                consecutiveFailures = 0,
                circuitOpenedAt = null,
            )
    }

    /** 记录一次失败：连续失败达阈值即熔断。 */
    fun recordFailure(
        id: String,
        now: Long,
    ) {
        val current = healthOf(id)
        val consecutive = current.consecutiveFailures + 1
        val openedAt =
            if (consecutive >= failureThreshold) {
                current.circuitOpenedAt ?: now
            } else {
                current.circuitOpenedAt
            }
        states[id] =
            current.copy(
                failureCount = current.failureCount + 1,
                consecutiveFailures = consecutive,
                circuitOpenedAt = openedAt,
            )
    }

    /**
     * 当前处于熔断（不可用）状态的源 id 集合。
     *
     * 冷却期满视为半开——此时仍会被选中一次，失败则重新熔断。
     */
    fun openCircuitIds(now: Long): Set<String> =
        states.filterValues { it.isCircuitOpen(now, cooldownMillis) }.keys

    /** 按健康分排序（分数高的优先），用于自动选源。 */
    fun rankByHealth(
        candidates: List<String>,
        latencies: Map<String, Int>,
    ): List<String> =
        candidates.sortedByDescending { healthOf(it).score(latencies[it]) }

    companion object {
        const val DEFAULT_FAILURE_THRESHOLD = 3
        const val DEFAULT_COOLDOWN_MILLIS = 5 * 60 * 1000L
    }
}
