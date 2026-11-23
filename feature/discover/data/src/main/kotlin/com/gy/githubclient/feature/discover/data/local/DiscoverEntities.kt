/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.discover.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.gy.githubclient.feature.discover.domain.model.DiscoverCategory
import com.gy.githubclient.feature.discover.domain.model.DiscoverItem
import com.gy.githubclient.feature.discover.domain.model.FeaturedRepo
import com.gy.githubclient.feature.discover.domain.model.ReleaseAsset
import com.gy.githubclient.feature.discover.domain.model.ReleaseInfo

/** 发现条目缓存：完整快照以 JSON 列存储，避免为 Release 建多张关联表。 */
@Entity(tableName = "discover_items")
data class DiscoverItemEntity(
    @PrimaryKey val key: String,
    val category: String,
    val owner: String,
    val name: String,
    val tagline: String,
    val topicsCsv: String,
    val latestReleaseJson: String?,
    val fetchedAt: String?,
)

fun DiscoverItem.toEntity(): DiscoverItemEntity =
    DiscoverItemEntity(
        key = featured.fullName,
        category = featured.category.name,
        owner = featured.owner,
        name = featured.name,
        tagline = featured.tagline,
        topicsCsv = featured.topics.joinToString(","),
        latestReleaseJson = latestRelease?.toJson(),
        fetchedAt = fetchedAt,
    )

fun DiscoverItemEntity.toDomain(): DiscoverItem =
    DiscoverItem(
        featured =
            FeaturedRepo(
                owner = owner,
                name = name,
                category = DiscoverCategory.fromWire(category),
                tagline = tagline,
                topics = topicsCsv.split(",").filter { it.isNotBlank() },
            ),
        latestRelease = latestReleaseJson?.let { parseRelease(it) },
        fetchedAt = fetchedAt,
    )

// 轻量 JSON 编解码：用 kotlinx.serialization 的 Json 对象在 Mapper 中完成。
private fun ReleaseInfo.toJson(): String = ReleaseJson.encode(this)

private fun parseRelease(json: String): ReleaseInfo? = ReleaseJson.decode(json)

internal object ReleaseJson {
    private val codec =
        kotlinx.serialization.json.Json {
            ignoreUnknownKeys = true
        }

    fun encode(info: ReleaseInfo): String = codec.encodeToString(ReleaseInfoWire.serializer(), info.toWire())

    fun decode(json: String): ReleaseInfo? =
        runCatching { codec.decodeFromString(ReleaseInfoWire.serializer(), json).toDomain() }.getOrNull()
}

@kotlinx.serialization.Serializable
internal data class ReleaseInfoWire(
    val tagName: String,
    val name: String,
    val publishedAt: String?,
    val htmlUrl: String,
    val body: String?,
    val isPrerelease: Boolean,
    val isDraft: Boolean,
    val assets: List<ReleaseAssetWire>,
)

@kotlinx.serialization.Serializable
internal data class ReleaseAssetWire(
    val id: Long,
    val name: String,
    val size: Long,
    val downloadUrl: String,
    val contentType: String,
    val downloadCount: Long,
)

private fun ReleaseInfo.toWire() =
    ReleaseInfoWire(
        tagName,
        name,
        publishedAt,
        htmlUrl,
        body,
        isPrerelease,
        isDraft,
        assets.map { ReleaseAssetWire(it.id, it.name, it.size, it.downloadUrl, it.contentType, it.downloadCount) },
    )

private fun ReleaseInfoWire.toDomain() =
    ReleaseInfo(
        tagName,
        name,
        publishedAt,
        htmlUrl,
        body,
        isPrerelease,
        isDraft,
        assets.map { ReleaseAsset(it.id, it.name, it.size, it.downloadUrl, it.contentType, it.downloadCount) },
    )
