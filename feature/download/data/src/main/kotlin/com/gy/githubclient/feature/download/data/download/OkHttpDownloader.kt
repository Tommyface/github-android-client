/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.data.download

import com.gy.githubclient.core.data.download.ContentRange
import com.gy.githubclient.core.data.download.PartialOwnership
import com.gy.githubclient.core.data.download.PartialRetry
import com.gy.githubclient.core.data.download.PartialReuse
import com.gy.githubclient.core.data.download.ProgressThrottle
import com.gy.githubclient.core.data.download.RangeDecision
import com.gy.githubclient.core.data.download.RangeRequest
import com.gy.githubclient.core.domain.model.installation.DownloadProgress
import com.gy.githubclient.core.domain.model.mirror.MirrorConfig
import com.gy.githubclient.core.domain.model.mirror.MirrorHealthRegistry
import com.gy.githubclient.core.domain.model.mirror.MirrorPreference
import com.gy.githubclient.core.domain.network.AssetIdentity
import com.gy.githubclient.core.domain.network.Downloader
import com.gy.githubclient.core.domain.repository.MirrorRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * 下载引擎（OkHttp 实现 core.Downloader）：
 * - 镜像优先失败回退直连（默认直连偏好则跳过镜像）；
 * - HTTP Range 断点续传，复用 core 的 PartialNaming/PartialOwnership/RangeRequest；
 * - 进度经 ProgressThrottle 节流（100ms 或百分比变化才发）；
 * - 3 次指数退避重试；支持按文件名取消。
 */
@Suppress("TooGenericExceptionCaught", "LongParameterList")
class OkHttpDownloader(
    private val httpClient: OkHttpClient,
    private val fileStore: DownloadFileStore,
    private val mirrorRepository: MirrorRepository,
    /** M1-05：镜像健康登记簿（成功/失败累计与熔断），全进程共享。 */
    private val mirrorHealth: MirrorHealthRegistry = MirrorHealthRegistry(),
) : Downloader {
    private val activeCalls = ConcurrentHashMap<String, MutableList<Call>>()

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun download(
        url: String,
        suggestedFileName: String?,
        bypassMirror: Boolean,
        identity: AssetIdentity?,
    ): Flow<DownloadProgress> =
        callbackFlow {
            val fileName = suggestedFileName ?: url.substringAfterLast('/', "download.bin")
            val calls = mutableListOf<Call>()
            activeCalls[fileName] = calls

            try {
                val preference = mirrorRepository.observePreference().first()
                val catalog = mirrorRepository.observeCatalog().first()
                val latencies = mirrorRepository.observeMeasuredLatencies().first()
                // v0.5.3 M1：选定镜像失败 → 池内其他镜像（按延迟）→ 直连，全池轮换。
                // M1-05：熔断冷却期的镜像不再进入候选链；直连与自定义模板不参与熔断统计。
                val chain = buildCandidateChain(url, preference, catalog, latencies, bypassMirror)

                val lastError = runChain(chain, fileName, identity, calls) { progress -> trySend(progress) }
                if (lastError != null) {
                    throw lastError
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 下载失败以 0 进度 + 结束态通知调用方，由上层任务状态机标记 FAILED。
                trySend(DownloadProgress(0L, null, null))
            } finally {
                activeCalls.remove(fileName)
                close()
            }
            awaitClose { calls.forEach { it.cancel() } }
        }

    /**
     * 构建候选链（M1-05：熔断中的源不参与）。
     */
    private fun buildCandidateChain(
        url: String,
        preference: MirrorPreference,
        catalog: List<MirrorConfig>,
        latencies: Map<String, Int>,
        bypassMirror: Boolean,
    ): List<Pair<String?, String>> {
        if (bypassMirror) return listOf(null to url)
        return MirrorUrlResolver.candidateChain(
            githubUrl = url,
            preference = preference,
            catalog = catalog,
            latencies = latencies,
            openCircuitIds = mirrorHealth.openCircuitIds(System.currentTimeMillis()),
        )
    }

    /**
     * 依次尝试候选源；成功返回 null，失败返回最后一个错误。
     *
     * 切换镜像时保留 .part，让下一候选从已收字节偏移续传；
     * 若新源不兼容（返回 200 全量），performOnce 内 TRUNCATE_REWRITE 会从头覆盖。
     * 仅当服务端明确资源不再存在（404/410）才丢弃（见 performOnce 内决策）。
     */
    private suspend fun runChain(
        chain: List<Pair<String?, String>>,
        fileName: String,
        identity: AssetIdentity?,
        calls: MutableList<Call>,
        onProgress: (DownloadProgress) -> Unit,
    ): IOException? {
        var lastError: IOException? = null
        for ((mirrorId, candidate) in chain) {
            try {
                performDownload(candidate, fileName, identity ?: AssetIdentity(), calls, onProgress)
                mirrorId?.let { mirrorHealth.recordSuccess(it) }
                return null
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                lastError = e
                mirrorId?.let { mirrorHealth.recordFailure(it, System.currentTimeMillis()) }
            }
        }
        return lastError ?: IOException("下载失败：无可用下载源")
    }

    /** 单源下载：断点续传 + 流式写文件 + 进度节流 + 有限重试。 */
    @Suppress("NestedBlockDepth", "ReturnCount")
    private suspend fun performDownload(
        url: String,
        fileName: String,
        identity: AssetIdentity,
        calls: MutableList<Call>,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        var attempt = 0
        while (PartialRetry.hasAttemptsLeft(attempt)) {
            try {
                performOnce(url, fileName, identity, calls, onProgress)
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                attempt++
                if (!PartialRetry.hasAttemptsLeft(attempt)) throw e
                delay(PartialRetry.backoffMillis(attempt - 1))
            }
        }
    }

    /** 单次尝试：Range 决策 + 写入 + 完成。 */
    @Suppress("CyclomaticComplexMethod", "NestedBlockDepth", "ThrowsCount")
    private suspend fun performOnce(
        url: String,
        fileName: String,
        identity: AssetIdentity,
        calls: MutableList<Call>,
        onProgress: (DownloadProgress) -> Unit,
    ) {
        val partLength = fileStore.partLength(fileName)
        val meta = fileStore.readMeta(fileName)
        val incoming = fileStore.toIdentity(fileName, identity)
        val reuse = PartialOwnership.decideReuse(partLength > 0, partLength, meta, incoming)
        val effectivePartLength =
            if (reuse == PartialReuse.REUSE) {
                partLength
            } else {
                fileStore.deletePartAndMeta(fileName)
                0L
            }

        val requestBuilder =
            Request
                .Builder()
                .url(url)
                .header("User-Agent", "GitHub-Android-Client/1.0")
        RangeRequest.headerValue(effectivePartLength)?.let { requestBuilder.header("Range", it) }

        val call = httpClient.newCall(requestBuilder.build())
        calls.add(call)
        val response = call.executeSuspend()
        try {
            // M12：必须解析服务端 Content-Range。传 null 会让 206 恒判 RESTART，
            // 断点续传形同虚设；只有起点与已收字节一致（APPEND）才可安全续写。
            val contentRange = ContentRange.parse(response.header("Content-Range"))
            val decision = RangeRequest.decide(response.code, effectivePartLength, contentRange)
            if (decision == RangeDecision.UNEXPECTED) {
                throw IOException("Unexpected HTTP ${response.code}")
            }

            if (decision == RangeDecision.RESTART) {
                // 起点不符 / 416：旧 .part 与新源字节流不对齐，丢弃后从头重写，
                // 避免「删文件却仍按旧偏移追加」写出缺头损坏文件。
                fileStore.deletePartAndMeta(fileName)
            }

            val startOffset =
                when (decision) {
                    RangeDecision.APPEND -> effectivePartLength
                    RangeDecision.TRUNCATE_REWRITE,
                    RangeDecision.RESTART,
                    RangeDecision.UNEXPECTED,
                    -> 0L
                }
            // 仅 APPEND 续写；RESTART/TRUNCATE_REWRITE 一律从头覆盖。
            val append = decision == RangeDecision.APPEND && startOffset > 0L

            val body = response.body ?: throw IOException("Empty response body")
            val contentLength = body.contentLength()
            val total =
                if (contentLength > 0) {
                    startOffset + contentLength
                } else if (identity.size > 0) {
                    identity.size
                } else {
                    null
                }

            val out = FileOutputStream(fileStore.partFile(fileName), append)
            val buffer = ByteArray(BUFFER_SIZE)
            var written = startOffset
            val throttle = ProgressThrottle()
            try {
                body.byteStream().use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        written += read
                        val progress =
                            DownloadProgress(
                                bytesDownloaded = written,
                                totalBytes = total,
                                percent = total?.takeIf { it > 0 }?.let { ((written * 100) / it).toInt().coerceIn(0, 100) },
                            )
                        if (throttle.shouldEmit(progress)) onProgress(progress)
                    }
                }
            } finally {
                out.flush()
                out.close()
            }

            fileStore.writeMeta(fileName, fileStore.toIdentity(fileName, identity.copy(size = total ?: written)))
            fileStore.finalize(fileName) ?: throw IOException("Failed to finalize download")
            onProgress(DownloadProgress(written, total ?: written, 100))
        } finally {
            response.close()
        }
    }

    override suspend fun saveToFile(
        url: String,
        suggestedFileName: String?,
    ): String {
        val fileName = suggestedFileName ?: url.substringAfterLast('/', "download.bin")
        var result: String? = null
        download(url, fileName, bypassMirror = false).collect { progress ->
            if (progress.percent == 100) {
                result = fileStore.getDownloadedPath(fileName)
            }
        }
        return result ?: fileStore.getDownloadedPath(fileName) ?: throw IOException("下载未完成")
    }

    override suspend fun getDownloadedFilePath(fileName: String): String? = fileStore.getDownloadedPath(fileName)

    override suspend fun cancelDownload(fileName: String): Boolean {
        val calls = activeCalls.remove(fileName) ?: return false
        calls.forEach { it.cancel() }
        return true
    }

    override suspend fun discardPartial(fileName: String): Boolean {
        fileStore.deletePartAndMeta(fileName)
        return true
    }

    override suspend fun reclaimOrphanedPartials(claimedNames: Set<String>): Int = fileStore.reclaimOrphans(claimedNames)

    private suspend fun Call.executeSuspend(): Response =
        suspendCancellableCoroutine { cont ->
            enqueue(
                object : okhttp3.Callback {
                    override fun onFailure(
                        call: Call,
                        e: IOException,
                    ) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }

                    override fun onResponse(
                        call: Call,
                        response: Response,
                    ) {
                        if (cont.isActive) {
                            cont.resume(response)
                        } else {
                            response.close()
                        }
                    }
                },
            )
            cont.invokeOnCancellation { cancel() }
        }

    private companion object {
        const val BUFFER_SIZE = 64 * 1024
    }
}
