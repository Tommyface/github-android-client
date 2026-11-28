/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.repo.presentation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 危险操作门禁的纯状态用例：界面靠 [RepoSettingsUiState.needsConfirm] 决定是否弹二次确认。
 *
 * 覆盖三类回归：
 *  - 未加载成功时不得要求确认（否则一进页面点保存就弹窗）；
 *  - 可见性 / 归档的方向性变更必须要求确认；
 *  - 保存成功后基线推进，同一变更不再重复要求确认。
 */
class RepoSettingsUiStateTest {
    @Test
    fun notLoadedNeverNeedsConfirm() {
        val state = RepoSettingsUiState(isPrivate = true, savedIsPrivate = false)
        assertFalse(state.visibilityChanged)
        assertFalse(state.needsConfirm)
    }

    @Test
    fun visibilityFlipNeedsConfirm() {
        val state =
            RepoSettingsUiState(
                loaded = true,
                isPrivate = true,
                savedIsPrivate = false,
            )
        assertTrue(state.visibilityChanged)
        assertFalse(state.archivedChanged)
        assertTrue(state.needsConfirm)
    }

    @Test
    fun archiveFlipNeedsConfirm() {
        val state =
            RepoSettingsUiState(
                loaded = true,
                isArchived = false,
                savedIsArchived = true,
            )
        assertFalse(state.visibilityChanged)
        assertTrue(state.archivedChanged)
        assertTrue(state.needsConfirm)
    }

    @Test
    fun descriptionOnlyChangeNeedsNoConfirm() {
        val state =
            RepoSettingsUiState(
                loaded = true,
                description = "new description",
                isPrivate = false,
                savedIsPrivate = false,
                isArchived = false,
                savedIsArchived = false,
            )
        assertFalse(state.needsConfirm)
    }

    @Test
    fun baselineResyncClearsConfirm() {
        val pending =
            RepoSettingsUiState(
                loaded = true,
                isPrivate = true,
                savedIsPrivate = false,
            )
        assertTrue(pending.needsConfirm)

        val afterSave = pending.copy(savedIsPrivate = pending.isPrivate, savedIsArchived = pending.isArchived)
        assertFalse(afterSave.visibilityChanged)
        assertFalse(afterSave.needsConfirm)
    }
}
