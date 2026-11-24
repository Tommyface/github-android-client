/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.library.domain.usecase

import com.gy.githubclient.feature.library.domain.model.TrackedApp
import com.gy.githubclient.feature.library.domain.repository.LibraryRepository
import kotlinx.coroutines.flow.Flow

class ObserveTrackedUseCase(
    private val repository: LibraryRepository,
) {
    operator fun invoke(): Flow<List<TrackedApp>> = repository.observeTracked()
}

class AddTrackedUseCase(
    private val repository: LibraryRepository,
) {
    suspend operator fun invoke(app: TrackedApp) = repository.addTracked(app)
}

class RemoveTrackedUseCase(
    private val repository: LibraryRepository,
) {
    suspend operator fun invoke(fullName: String) = repository.removeTracked(fullName)
}

class CheckUpdatesUseCase(
    private val repository: LibraryRepository,
) {
    suspend operator fun invoke(apps: List<TrackedApp>) = repository.checkUpdates(apps)
}
