/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.presentation.di

import com.gy.githubclient.feature.auth.presentation.AuthViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/** auth presentation 层 DI：ViewModel。 */
val authPresentationModule =
    module {
        viewModel {
            AuthViewModel(
                signInWithPat = get(),
                signInWithOAuth = get(),
                bindNotificationToken = get(),
                startDeviceFlow = get(),
                pollDeviceToken = get(),
                observeAuthState = get(),
                signOut = get(),
                restoreSession = get(),
            )
        }
    }
