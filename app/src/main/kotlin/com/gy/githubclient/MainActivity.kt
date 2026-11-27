/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.gy.githubclient.core.domain.link.GitHubLink
import com.gy.githubclient.core.domain.link.GitHubLinkParser
import com.gy.githubclient.core.presentation.theme.GithubClientTheme
import com.gy.githubclient.core.presentation.theme.ThemeMode
import com.gy.githubclient.feature.auth.domain.model.AuthState
import com.gy.githubclient.feature.auth.presentation.AuthViewModel
import com.gy.githubclient.feature.auth.presentation.LoginScreen
import com.gy.githubclient.navigation.AppNavigation
import com.gy.githubclient.navigation.Route
import com.gy.githubclient.settings.AppSettingsStore
import com.gy.githubclient.ui.AppScaffold
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

class MainActivity : ComponentActivity() {
    /** 启动或分享进来的 GitHub 链接；登录前先挂起，进入主界面后消费（v0.16.0）。 */
    private var pendingLink by mutableStateOf<GitHubLink?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // v0.19.0：edge-to-edge 从 y=0 绘制，顶部通栏才能与状态栏无缝融合。
        // 状态栏底色保持 enableEdgeToEdge 默认的透明（不含额外遮罩），玻璃色从 y=0 起铺满。
        enableEdgeToEdge()
        pendingLink = extractLink(intent)
        requestNotificationPermissionIfNeeded()
        setContent {
            val settingsStore: AppSettingsStore = koinInject()
            val settings by settingsStore.settings.collectAsStateWithLifecycle()
            // 状态栏/导航栏图标明暗跟随实际生效的主题（浅色底用深色图标，反之亦然）。
            val darkTheme =
                when (settings.themeMode) {
                    ThemeMode.SYSTEM -> isSystemInDarkTheme()
                    ThemeMode.LIGHT -> false
                    ThemeMode.DARK -> true
                }
            LaunchedEffect(darkTheme) { applyBarIconAppearance(darkTheme) }
            GithubClientTheme(
                themeMode = settings.themeMode,
                codeFontSize = settings.codeFontSizeSp.sp,
            ) {
                // 页面底色由主题根节点的 FrostPageBackground 提供，这里保持透明。
                Surface(modifier = Modifier.fillMaxSize(), color = Color.Transparent) {
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
                                    startLink = pendingLink,
                                    onLinkConsumed = { pendingLink = null },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    /** 透明状态栏下，图标明暗必须由应用自己决定，否则深色主题会出现"黑图标压深底"。 */
    private fun applyBarIconAppearance(darkTheme: Boolean) {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        extractLink(intent)?.let { pendingLink = it }
    }

    /**
     * 从 Intent 提取 GitHub 链接：
     * - ACTION_VIEW（githubclient:// 或 github.com）直接严格解析；
     * - ACTION_SEND 只从纯文本里提取显式链接，认不出就忽略，绝不影响正常启动。
     */
    private fun extractLink(intent: Intent?): GitHubLink? {
        if (intent == null) return null
        return when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString?.let(GitHubLinkParser::parse)
            Intent.ACTION_SEND ->
                if (intent.type?.startsWith("text/") == true) {
                    intent.getStringExtra(Intent.EXTRA_TEXT)?.let(GitHubLinkParser::parseFromText)
                } else {
                    null
                }

            else -> null
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
