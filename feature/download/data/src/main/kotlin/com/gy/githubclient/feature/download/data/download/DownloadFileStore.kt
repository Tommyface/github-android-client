/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.data.download

import android.content.Context
import com.gy.githubclient.core.data.download.PartialIdentity
import com.gy.githubclient.core.data.download.PartialNaming
import com.gy.githubclient.core.domain.network.AssetIdentity
import java.io.File

/** 下载目录与断点续传文件操作（.part/.meta）。 */
class DownloadFileStore(
    private val context: Context,
) {
    private val downloadDir: File
        get() = File(context.filesDir, DIR_NAME).also { it.mkdirs() }

    fun destination(fileName: String): File = File(downloadDir, safeName(fileName))

    fun partFile(fileName: String): File = destination(PartialNaming.part(safeName(fileName)))

    fun metaFile(fileName: String): File = destination(PartialNaming.meta(safeName(fileName)))

    fun readMeta(fileName: String): PartialIdentity? =
        runCatching {
            val text = metaFile(fileName).readText()
            PartialIdentity.parse(text)
        }.getOrNull()

    fun writeMeta(
        fileName: String,
        identity: PartialIdentity,
    ) {
        metaFile(fileName).writeText(identity.serialize())
    }

    fun partLength(fileName: String): Long = partFile(fileName).let { if (it.exists()) it.length() else 0L }

    fun deletePartAndMeta(fileName: String) {
        partFile(fileName).delete()
        metaFile(fileName).delete()
    }

    fun deleteDestination(fileName: String) {
        destination(fileName).delete()
        deletePartAndMeta(fileName)
    }

    fun getDownloadedPath(fileName: String): String? =
        destination(fileName).takeIf { it.exists() }?.absolutePath

    fun listFiles(): List<File> = downloadDir.listFiles()?.toList().orEmpty()

    /** 完成下载：把 .part 重命名为目标文件并清理 .meta。 */
    fun finalize(fileName: String): String? {
        val part = partFile(fileName)
        val dest = destination(fileName)
        if (!part.exists()) return null
        // 若目标已存在（digest 校验场景），先删除再重命名。
        if (dest.exists()) dest.delete()
        return if (part.renameTo(dest)) {
            metaFile(fileName).delete()
            dest.absolutePath
        } else {
            null
        }
    }

    /** 孤儿清理：删除未被任务占用的 .part/.meta。 */
    fun reclaimOrphans(claimedNames: Set<String>): Int {
        var removed = 0
        listFiles().forEach { file ->
            val name = file.name
            val isPart = PartialNaming.isPartFile(name)
            val isMeta = PartialNaming.isMetaFile(name)
            if (!isPart && !isMeta) return@forEach
            val assetName =
                if (isMeta) {
                    PartialNaming.assetNameOfMeta(name)
                } else {
                    PartialNaming.assetNameOfPart(name)
                }
            if (assetName != null && assetName !in claimedNames) {
                if (file.delete()) removed++
            }
        }
        return removed
    }

    fun toIdentity(
        fileName: String,
        asset: AssetIdentity,
    ): PartialIdentity =
        PartialIdentity(
            assetId = asset.assetId,
            digest = asset.digest,
            size = if (asset.size > 0) asset.size else partLength(fileName),
        )

    private fun safeName(name: String): String =
        name
            .replace('/', '_')
            .replace('\\', '_')
            .replace("..", "_")
            .ifBlank { "download.bin" }

    private companion object {
        const val DIR_NAME = "downloads"
    }
}
