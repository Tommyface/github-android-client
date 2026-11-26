/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.gy.githubclient.core.presentation.theme.GithubClientTheme
import com.gy.githubclient.feature.auth.domain.model.AuthState
import com.gy.githubclient.feature.auth.presentation.AuthViewModel
import com.gy.githubclient.feature.auth.presentation.LoginScreen
import com.gy.githubclient.navigation.AppNavigation
import com.gy.githubclient.navigation.Route
import com.gy.githubclient.ui.AppScaffold
import org.koin.androidx.compose.koinViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        setContent {
            // Frost 设计系统（P-200 收编）：全 App 统一走令牌派生的 Material3 色板，
            // 页面底色与光斑由 FrostPageBackground 绘制一次，滚动不再重绘底图。
            GithubClientTheme {
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
                                AppNavigation(
                                    navController = navController,
                                    // 自更新通知点击直达「检查更新」页（应用内下载闭环）。
                                    startDestination =
                                        if (intent.getBooleanExtra(EXTRA_OPEN_UPDATE_CHECK, false)) {
                                            Route.UpdateCheck.path
                                        } else {
                                            Route.Home.path
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** 自更新通知依赖 POST_NOTIFICATIONS；Android 13+ 首启弹一次系统授权框，拒绝不影响功能。 */
    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
            .launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private companion object {
        const val EXTRA_OPEN_UPDATE_CHECK = "open_update_check"
    }
}

@Composable
private fun LoadingBox() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}
