/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.download.data.mirror

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gy.githubclient.core.domain.model.mirror.MirrorPreference
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.mirrorDataStore: DataStore<Preferences> by preferencesDataStore(name = "mirror_prefs")

/** 镜像偏好与测速结果的 DataStore 持久化。 */
class MirrorPreferencesStore(
    private val context: Context,
) {
    private val preferenceKey = stringPreferencesKey("mirror_preference")
    private val latenciesKey = stringPreferencesKey("mirror_latencies")

    fun observePreference(): Flow<MirrorPreference> =
        context.mirrorDataStore.data.map { prefs ->
            when (val raw = prefs[preferenceKey]) {
                null, "direct" -> MirrorPreference.Direct
                else ->
                    when {
                        raw.startsWith("custom:") -> MirrorPreference.Custom(raw.removePrefix("custom:"))
                        raw.startsWith("selected:") -> MirrorPreference.Selected(raw.removePrefix("selected:"))
                        else -> MirrorPreference.Direct
                    }
            }
        }

    suspend fun setPreference(preference: MirrorPreference) {
        context.mirrorDataStore.edit { prefs ->
            prefs[preferenceKey] =
                when (preference) {
                    MirrorPreference.Direct -> "direct"
                    is MirrorPreference.Selected -> "selected:${preference.id}"
                    is MirrorPreference.Custom -> "custom:${preference.template}"
                }
        }
    }

    fun observeLatencies(): Flow<Map<String, Int>> =
        context.mirrorDataStore.data.map { prefs ->
            (prefs[latenciesKey] ?: "")
                .split(";")
                .mapNotNull { entry ->
                    val parts = entry.split("=")
                    if (parts.size == 2) parts[0] to (parts[1].toIntOrNull() ?: return@mapNotNull null) else null
                }.toMap()
        }

    suspend fun setLatencies(latencies: Map<String, Int>) {
        context.mirrorDataStore.edit { prefs ->
            prefs[latenciesKey] = latencies.entries.joinToString(";") { "${it.key}=${it.value}" }
        }
    }
}
