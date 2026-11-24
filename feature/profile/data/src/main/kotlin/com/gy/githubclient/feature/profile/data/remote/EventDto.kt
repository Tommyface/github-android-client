/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.data.remote

import com.gy.githubclient.feature.profile.domain.model.UserEvent
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** GET /users/{login}/events/public 单条事件 DTO（仅映射展示所需子集）。 */
@Serializable
data class EventDto(
    val id: String = "",
    val type: String = "",
    val repo: EventRepoDto = EventRepoDto(),
    @SerialName("created_at") val createdAt: String = "",
    val payload: EventPayloadDto = EventPayloadDto(),
)

@Serializable
data class EventRepoDto(
    @SerialName("name") val fullName: String = "",
)

@Serializable
data class EventPayloadDto(
    val ref: String? = null,
    val head: String? = null,
)

fun EventDto.toDomain(): UserEvent =
    UserEvent(
        id = id,
        type = type,
        repoFullName = repo.fullName,
        createdAt = createdAt,
        ref = payload.ref.orEmpty(),
        head = payload.head.orEmpty(),
    )
