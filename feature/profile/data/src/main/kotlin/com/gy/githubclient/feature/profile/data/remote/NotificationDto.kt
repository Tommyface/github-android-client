/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.data.remote

import com.gy.githubclient.feature.profile.domain.model.GitHubNotification
import com.gy.githubclient.feature.profile.domain.model.NotificationSubject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /notifications 响应元素（v0.6.0 M7）。默认值保证缺失字段可安全反序列化。 */
@Serializable
data class NotificationDto(
    val id: String = "",
    val unread: Boolean = true,
    val reason: String = "",
    val subject: SubjectDto = SubjectDto(),
    val repository: RepoDto = RepoDto(),
    @SerialName("updated_at") val updatedAt: String? = null,
) {
    @Serializable
    data class SubjectDto(
        val title: String = "",
        val type: String = "",
        val url: String? = null,
    )

    @Serializable
    data class RepoDto(
        @SerialName("full_name") val fullName: String = "",
        @SerialName("html_url") val htmlUrl: String = "",
    )
}

/** 线程 id：GitHub 通知 id 为十进制字符串，转 Long 供标记已读端点使用。 */
internal fun NotificationDto.toThreadId(): Long = id.toLongOrNull() ?: 0L

fun NotificationDto.toDomain(): GitHubNotification =
    GitHubNotification(
        id = toThreadId(),
        unread = unread,
        reason = reason,
        subject = NotificationSubject(title = subject.title, type = subject.type, url = subject.url),
        repoFullName = repository.fullName,
        updatedAt = updatedAt,
    )
