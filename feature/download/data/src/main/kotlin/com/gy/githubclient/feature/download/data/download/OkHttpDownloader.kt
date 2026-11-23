/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.data.download

import com.gy.githubclient.core.data.download.PartialOwnership
import com.gy.githubclient.core.data.download.PartialRetry
import com.gy.githubclient.core.data.download.PartialReuse
import com.gy.githubclient.core.data.download.ProgressThrottle
import com.gy.githubclient.core.data.download.RangeDecision
import com.gy.githubclient.core.data.download.RangeRequest
import com.gy.githubclient.core.domain.model.installation.DownloadProgress
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
                val mirrorUrl =
                    if (bypassMirror) {
                        null
                    } else {
                        MirrorUrlResolver.resolve(url, preference, catalog)
                    }
                val urls = listOfNotNull(mirrorUrl, url).distinct()

                var lastError: IOException? = null
                var succeeded = false
                for (candidate in urls) {
                    try {
                        performDownload(candidate, fileName, identity ?: AssetIdentity(), calls) { progress ->
                            trySend(progress)
                        }
                        succeeded = true
                        break
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IOException) {
                        lastError = e
                        // 镜像失败：清掉可能不兼容的 .part，回退直连从头下。
                        if (candidate != url) fileStore.deletePartAndMeta(fileName)
                    }
                }
                if (!succeeded) {
                    throw lastError ?: IOException("下载失败：无可用下载源")
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
            val decision = RangeRequest.decide(response.code, effectivePartLength, null)
            if (decision == RangeDecision.UNEXPECTED) {
                throw IOException("Unexpected HTTP ${response.code}")
            }

            val startOffset =
                when (decision) {
                    RangeDecision.APPEND,
                    RangeDecision.RESTART,
                    -> effectivePartLength

                    RangeDecision.TRUNCATE_REWRITE -> 0L
                    RangeDecision.UNEXPECTED -> 0L
                }
            if (decision == RangeDecision.RESTART) {
                fileStore.deletePartAndMeta(fileName)
            }

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

            val append = decision == RangeDecision.APPEND || decision == RangeDecision.RESTART
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
