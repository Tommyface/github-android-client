/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.gy.githubclient.feature.auth.domain.model.AuthState
import com.gy.githubclient.feature.auth.presentation.AuthViewModel
import com.gy.githubclient.feature.auth.presentation.LoginScreen
import com.gy.githubclient.navigation.AppNavigation
import com.gy.githubclient.ui.AppScaffold
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val authViewModel: AuthViewModel = koinViewModel()
                    val authState by authViewModel.authState.collectAsStateWithLifecycle()

                    when (authState) {
                        AuthState.Loading -> LoadingBox()

                        AuthState.SignedOut ->
                            LoginScreen(onLoginSuccess = { /* 状态流驱动自动切换 */ })

                        is AuthState.SignedIn -> {
                            val navController = rememberNavController()
                            AppScaffold(navController = navController) {
                                AppNavigation(navController = navController)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
