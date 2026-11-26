package com.ghhub.app.mirror

/**
 * 选源权重（方案 6.3）：成功率 ÷ 延迟，失败记录计入下次选源。
 * 下载失败时按权重切换下一源，解决 githup #39。
 */
object MirrorSelector {

    fun weight(src: MirrorSource): Double {
        val latency = src.latencyMs ?: return 0.0
        if (latency <= 0) return 0.0
        val total = src.successCount + src.failCount
        val successRate = if (total == 0) 0.7 else src.successCount.toDouble() / (total + 1)
        return successRate / (latency / 100.0)
    }

    /** 返回按权重排序的可用源列表（已排除不可达 / 高丢包）。 */
    fun ranked(sources: List<MirrorSource>): List<MirrorSource> =
        sources.filter { it.latencyMs != null && it.lossPercent < 100 }
            .sortedByDescending { weight(it) }

    fun best(sources: List<MirrorSource>): MirrorSource? = ranked(sources).firstOrNull()

    /** 当前源的下一个候选（失败切换用）。 */
    fun nextAfter(current: MirrorSource, sources: List<MirrorSource>): MirrorSource? {
        val list = ranked(sources)
        val idx = list.indexOfFirst { it.id == current.id }
        return if (idx >= 0 && idx + 1 < list.size) list[idx + 1] else list.firstOrNull { it.id != current.id }
    }
}
