/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.presentation.di

import com.gy.githubclient.feature.profile.presentation.ActivityViewModel
import com.gy.githubclient.feature.profile.presentation.GistViewModel
import com.gy.githubclient.feature.profile.presentation.NotificationsViewModel
import com.gy.githubclient.feature.profile.presentation.OrgViewModel
import com.gy.githubclient.feature.profile.presentation.ProfileViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** profile presentation 层 DI：ViewModel。 */
val profilePresentationModule =
    module {
        viewModel {
            ProfileViewModel(
                observeProfile = get(),
                refreshProfile = get(),
                clearProfileCache = get(),
                signOutUseCase = get(),
            )
        }
        viewModel {
            NotificationsViewModel(
                listNotifications = get(),
                markRead = get(),
                markAllReadUseCase = get(),
                setThreadSubscription = get(),
            )
        }
        viewModel {
            ActivityViewModel(
                observeProfile = get(),
                listUserEvents = get(),
            )
        }
        viewModel {
            OrgViewModel(
                getOrg = get(),
                listOrgMembers = get(),
            )
        }
        viewModel {
            GistViewModel(
                observeProfile = get(),
                listUserGists = get(),
                getGist = get(),
            )
        }
    }
