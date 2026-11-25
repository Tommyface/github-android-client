/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.auth.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.core.presentation.theme.FrostCard
import com.gy.githubclient.core.presentation.theme.FrostDimens
import com.gy.githubclient.core.presentation.theme.LocalFrostColors
import com.gy.githubclient.feature.auth.domain.model.AuthState
import org.koin.androidx.compose.koinViewModel

/**
 * 登录路由。推荐 OAuth Device Flow 一键授权；PAT 为主通道备用；通知通道 classic PAT 可选绑定。
 *
 * v0.19.0 Frost：登录页不在 AppScaffold 内，直接吃系统栏 inset；三块表单全部落在玻璃卡片里。
 */
@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: AuthViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    var message by remember { mutableStateOf<String?>(null) }
    val frost = LocalFrostColors.current

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                AuthEvent.LoginSuccess -> onLoginSuccess()
                AuthEvent.NotificationBound -> message = "通知通道已绑定"
                is AuthEvent.ShowMessage -> message = event.message
            }
        }
    }

    if (authState is AuthState.SignedIn) {
        LaunchedEffect(authState) { onLoginSuccess() }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars),
        contentPadding =
            PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 24.dp,
                bottom = 32.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(FrostDimens.CardGap, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        item(key = "login_hero") {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "GitHub Client",
                    style = MaterialTheme.typography.headlineMedium,
                    color = frost.textPrimary,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "管理仓库 · 发现应用 · 加速下载",
                    style = MaterialTheme.typography.bodyMedium,
                    color = frost.textSecondary,
                )
                Spacer(Modifier.height(12.dp))
            }
        }

        item(key = "login_oauth") {
            OAuthDeviceFlowCard(
                deviceFlow = uiState.deviceFlow,
                onStart = {
                    message = null
                    viewModel.beginDeviceFlow()
                },
                onCancel = viewModel::cancelDeviceFlow,
            )
        }

        item(key = "login_pat") {
            FrostCard {
                Text(
                    text = "主通道 · Fine-grained Token（备用）",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "用于仓库、Issue、PR、Release、搜索等全部常规功能。",
                    style = MaterialTheme.typography.bodySmall,
                    color = frost.textSecondary,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = uiState.primaryToken,
                    onValueChange = viewModel::onPrimaryTokenChange,
                    label = { Text("github_pat_...") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
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
                if (message != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = message.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = frost.danger,
                    )
                }
            }
        }

        item(key = "login_notification") {
            FrostCard {
                Text(
                    text = "通知通道 · Classic Token（可选）",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = "仅 /notifications 支持 classic PAT，需勾选 notifications + repo 权限。" +
                        "不填则通知页走网页兜底。",
                    style = MaterialTheme.typography.bodySmall,
                    color = frost.textSecondary,
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
    val frost = LocalFrostColors.current
    FrostCard {
        Text(
            text = "GitHub 账号登录（推荐）",
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = "无需手动创建 Token：在浏览器输入 8 位授权码即可完成登录。",
            style = MaterialTheme.typography.bodySmall,
            color = frost.textSecondary,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text =
                "将申请读写仓库、通知、Actions、Gist、组织读取与关注用户所需的 scope；" +
                    "只用于你主动发起的操作，可在 GitHub 设置中随时撤销。",
            style = MaterialTheme.typography.labelSmall,
            color = frost.textTertiary,
        )
        Spacer(Modifier.height(12.dp))

        when (deviceFlow) {
            DeviceFlowUiState.Idle -> {
                Button(
                    onClick = onStart,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("使用 GitHub 账号登录")
                }
            }

            DeviceFlowUiState.Requesting -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(20.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        "正在获取授权码…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = frost.textSecondary,
                    )
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
                    color = frost.textSecondary,
                )
                Text(
                    text = deviceFlow.verificationUri,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    color = frost.link,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "第 2 步：输入以下授权码",
                    style = MaterialTheme.typography.bodySmall,
                    color = frost.textSecondary,
                )
                Text(
                    text = deviceFlow.userCode,
                    style = MaterialTheme.typography.headlineLarge,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 6.sp,
                    color = frost.textPrimary,
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
                        color = frost.textSecondary,
                    )
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onCancel) { Text("取消") }
                }
            }

            DeviceFlowUiState.Expired -> {
                Text(
                    text = "授权码已过期，请重新发起登录。",
                    style = MaterialTheme.typography.bodySmall,
                    color = frost.danger,
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
                    color = frost.danger,
                )
                Spacer(Modifier.height(8.dp))
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text("重新发起")
                }
            }
        }
    }
}
