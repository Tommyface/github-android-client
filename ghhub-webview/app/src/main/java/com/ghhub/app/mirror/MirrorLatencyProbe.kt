package com.ghhub.app.mirror

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 测速结果。 */
data class ProbeResult(val source: MirrorSource, val latencyMs: Long?, val lossPercent: Int, val reachable: Boolean)

/**
 * MirrorLatencyProbeImpl：对候选源并发测速 3 次取中位数（复用现有实现设计）。
 * 直连超时 → 自动降权；结果写回 MirrorSource。
 */
class MirrorLatencyProbe(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.SECONDS)
        .build()
) {
    suspend fun probeAll(sources: List<MirrorSource>, probeUrl: String): List<ProbeResult> = withContext(Dispatchers.IO) {
        sources.map { src ->
            val latencies = mutableListOf<Long>()
            var failures = 0
            repeat(3) {
                val t0 = System.currentTimeMillis()
                try {
                    val url = if (src.custom || src.id == "direct") "https://github.com/$probeUrl"
                              else src.template.replace("{url}", probeUrl)
                    val req = Request.Builder().url(url).head().build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) latencies.add(System.currentTimeMillis() - t0) else failures++
                    }
                } catch (e: Exception) {
                    failures++
                }
            }
            val median = latencies.sorted().getOrNull(latencies.size / 2)
            val loss = failures * 100 / 3
            src.latencyMs = median
            src.lossPercent = loss
            ProbeResult(src, median, loss, median != null)
        }.sortedBy { it.latencyMs ?: Long.MAX_VALUE }
    }
}
