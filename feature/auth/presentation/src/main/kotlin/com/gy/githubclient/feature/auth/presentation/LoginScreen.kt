/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.core.presentation.theme.FrostCard
import com.gy.githubclient.core.presentation.theme.LocalFrostColors
import com.gy.githubclient.feature.auth.domain.model.AuthState
import org.koin.androidx.compose.koinViewModel

/**
 * 登录路由。
 *
 * 卡片顺序即优先级：**Token 登录排第一**（Token 走 `api.github.com`，国内通常可达），
 * Device Flow 排第二（端点固定在 `github.com`，国内常被阻断）。
 *
 * 这个顺序是 v0.19.3 按真机反馈调整的：原来 Device Flow 是第一张卡且标题写"GitHub 账号登录"，
 * 用户点了它就一直停在「正在获取授权码」——可达的通道被放在第二屏，不可达的通道占着首屏。
 * 把能用的入口放第一位，比给不能用的入口继续加进度条更有用。
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val tokenFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AuthEvent.LoginSuccess -> onLoginSuccess()
                AuthEvent.NotificationBound -> message = "通知通道已绑定"
                is AuthEvent.ShowMessage -> message = event.message
            }
        }
    }

    // Device Flow 因传输层问题发起失败或轮询中止时，把用户送回 Token 卡：
    // 滚回顶部（Token 卡是第一张）+ 聚焦输入框，键盘直接弹出，用户只需粘贴。
    // 只弹一条错误提示、让用户自己在三张卡之间找出口，是上一版最招人烦的地方。
    LaunchedEffect(uiState.suggestTokenLogin) {
        if (uiState.suggestTokenLogin) {
            scrollState.animateScrollTo(0)
            tokenFocus.requestFocus()
        }
    }

    if (authState is AuthState.SignedIn) {
        LaunchedEffect(authState) { onLoginSuccess() }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                // 三张卡片（Token / Device Flow / 通知通道）在多数手机上超过一屏：
                // 原实现没有滚动容器，底部卡片和「绑定通知通道」会被直接裁掉——功能因此不可达。
                // 加 verticalScroll 后高度仍由 fillMaxSize 定，内容不足一屏时 Arrangement.Center 照旧生效。
                .verticalScroll(scrollState)
                .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "GitHub Client",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "管理仓库 · 发现应用 · 加速下载",
            style = MaterialTheme.typography.bodyMedium,
            color = LocalFrostColors.current.textTertiary,
        )

        // 错误提示提到页面级：Token 校验失败、通知通道绑定失败、Device Flow 中止都会走到这里。
        // 原先它挂在 Token 卡片内部，导致"通知通道 Token 无效"也显示在 Token 卡里，指向错误。
        if (message != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = message.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = LocalFrostColors.current.danger,
                // 错误信息是**状态更新**：必须主动朗读，否则弱视用户完全感知不到失败。
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
        }

        Spacer(Modifier.height(32.dp))

        FrostCard {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "GitHub 账号登录 · Token（推荐）",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text =
                        "粘贴一次 Token 即可登录。Token 走 api.github.com（国内通常可达）；" +
                            "创建路径：GitHub → Settings → Developer settings → Personal access tokens。",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalFrostColors.current.textTertiary,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.primaryToken,
                    onValueChange = viewModel::onPrimaryTokenChange,
                    label = { Text("github_pat_...") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            // 读屏时说明这是什么凭证、粘在哪里（label 只是占位示例）。
                            .semantics { contentDescription = "Fine-grained Token 输入框" }
                            .focusRequester(tokenFocus),
                    enabled = !uiState.isSubmitting,
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        message = null
                        viewModel.signIn()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isSubmitting,
                ) {
                    if (uiState.isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text("登录")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        OAuthDeviceFlowCard(
            deviceFlow = uiState.deviceFlow,
            onStart = {
                message = null
                viewModel.beginDeviceFlow()
            },
            onCancel = viewModel::cancelDeviceFlow,
        )

        Spacer(Modifier.height(16.dp))

        FrostCard {
            Column(Modifier.padding(16.dp)) {
                Text(
                    text = "通知通道 · Classic Token（可选）",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "仅 /notifications 支持 classic PAT，需勾选 notifications + repo 权限。不填则通知页走网页兜底。",
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalFrostColors.current.textTertiary,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.notificationToken,
                    onValueChange = viewModel::onNotificationTokenChange,
                    label = { Text("ghp_...") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isBindingNotification,
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    TextButton(
                        onClick = {
                            message = null
                            viewModel.bindNotification()
                        },
                    ) {
                        Text("绑定通知通道")
                    }
                }
            }
        }
    }
}

/** OAuth Device Flow 卡片：发起 → 展示 8 位授权码 → 等待浏览器授权 → 自动完成登录。 */
@Composable
private fun OAuthDeviceFlowCard(
    deviceFlow: DeviceFlowUiState,
    onStart: () -> Unit,
    onCancel: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val uriHandler = LocalUriHandler.current
    FrostCard {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "GitHub 授权登录 · 授权码（需能直连 github.com）",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text =
                    "在浏览器输入 8 位授权码即可完成登录，无需创建 Token。" +
                        "但该流程的端点固定在 github.com，国内常被阻断 —— 一直转圈请取消，改用上方 Token。",
                style = MaterialTheme.typography.bodySmall,
                color = LocalFrostColors.current.textTertiary,
            )
            Spacer(Modifier.height(12.dp))

            when (deviceFlow) {
                DeviceFlowUiState.Idle -> {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("发起授权码登录")
                    }
                }

                DeviceFlowUiState.Requesting -> {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp,
                            )
                            Text("正在获取授权码…", style = MaterialTheme.typography.bodyMedium)
                        }
                        // 之前这里只有一个转圈：失败前最长要等 96 秒，期间既没有进度也没有出口，
                        // 观感就是"卡死"。现在把预算（约 26 秒）与退路（取消 / 改用 Token）写在界面上。
                        Text(
                            text =
                                "最长约 26 秒；若是 github.com 无法直连会一直无响应，" +
                                    "可取消后改用上方 Token 登录。",
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalFrostColors.current.textTertiary,
                        )
                        TextButton(onClick = onCancel) { Text("取消") }
                    }
                }

                is DeviceFlowUiState.AwaitingAuthorization -> {
                    var remainingSeconds by remember(deviceFlow) {
                        mutableLongStateOf(
                            ((deviceFlow.expiresAtMillis - System.currentTimeMillis()) / 1000L)
                                .coerceAtLeast(0L),
                        )
                    }
                    LaunchedEffect(deviceFlow) {
                        while (remainingSeconds > 0L) {
                            kotlinx.coroutines.delay(1000L)
                            remainingSeconds =
                                ((deviceFlow.expiresAtMillis - System.currentTimeMillis()) / 1000L)
                                    .coerceAtLeast(0L)
                        }
                    }

                    Text(
                        text = "第 1 步：在任意浏览器打开",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = deviceFlow.verificationUri,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = LocalFrostColors.current.primaryInk,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "第 2 步：输入以下授权码",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        text = deviceFlow.userCode,
                        style = MaterialTheme.typography.headlineLarge,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 6.sp,
                        color = LocalFrostColors.current.textPrimary,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { clipboard.setText(AnnotatedString(deviceFlow.userCode)) },
                        ) {
                            Text("复制授权码")
                        }
                        OutlinedButton(
                            onClick = { uriHandler.openUri(deviceFlow.verificationUri) },
                        ) {
                            Text("打开授权页")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(16.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text =
                                if (remainingSeconds > 0L) {
                                    "等待授权中…有效期剩余 ${remainingSeconds / 60} 分 ${remainingSeconds % 60} 秒"
                                } else {
                                    "授权码已过期"
                                },
                            style = MaterialTheme.typography.bodySmall,
                            color = LocalFrostColors.current.textTertiary,
                        )
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = onCancel) { Text("取消") }
                    }
                }

                DeviceFlowUiState.Expired -> {
                    Text(
                        text = "授权码已过期，请重新发起登录。",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalFrostColors.current.danger,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Text("重新发起")
                    }
                }

                DeviceFlowUiState.Denied -> {
                    Text(
                        text = "你拒绝了对本应用的授权。",
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalFrostColors.current.danger,
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                        Text("重新发起")
                    }
                }
            }
        }
    }
}
