/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.feature.profile.presentation

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gy.githubclient.feature.profile.domain.model.UserProfile
import org.koin.androidx.compose.koinViewModel

/**
 * 「我的」页：展示当前用户画像与统计，提供星标仓库、通知、下载、镜像设置入口，并可退出登录。
 */
@Suppress("LongParameterList") // 导航回调逐一显式注入，属 Compose 页面常规形态
@Composable
fun ProfileScreen(
    onOpenStarred: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenMirrorSettings: () -> Unit,
    onOpenPermissionAudit: () -> Unit = {},
    onOpenNotifications: () -> Unit = {},
    onOpenUpdateCheck: () -> Unit = {},
    onOpenLibrary: () -> Unit = {},
    onOpenActivity: () -> Unit = {},
    onOpenOrg: () -> Unit = {},
    onOpenGists: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
    viewModel: ProfileViewModel = koinViewModel(),
) {
    val profile by viewModel.profile.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("我的", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(16.dp))

        if (profile == null) {
            Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            ProfileHeader(profile = profile!!)
        }

        Spacer(Modifier.height(16.dp))
        Text("功能", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp)) {
                FullWidthButton(text = "⭐ 星标仓库", onClick = onOpenStarred)
                HorizontalDivider()
                FullWidthButton(text = "🕒 个人动态", onClick = onOpenActivity)
                HorizontalDivider()
                FullWidthButton(text = "📝 我的 Gist", onClick = onOpenGists)
                HorizontalDivider()
                FullWidthButton(text = "🏢 组织信息", onClick = onOpenOrg)
                HorizontalDivider()
                FullWidthButton(text = "📱 应用库", onClick = onOpenLibrary)
                HorizontalDivider()
                FullWidthButton(text = "🔔 通知中心", onClick = onOpenNotifications)
                HorizontalDivider()
                FullWidthButton(text = "下载管理", onClick = onOpenDownloads)
                HorizontalDivider()
                FullWidthButton(text = "镜像加速设置", onClick = onOpenMirrorSettings)
                HorizontalDivider()
                FullWidthButton(text = "🎨 外观与缓存", onClick = onOpenSettings)
                HorizontalDivider()
                FullWidthButton(text = "🔐 权限体检", onClick = onOpenPermissionAudit)
                HorizontalDivider()
                FullWidthButton(text = "⬆️ 检查更新", onClick = onOpenUpdateCheck)
                HorizontalDivider()
                // v0.12.0 N9：贡献热力图仅 GraphQL contributionsCollection 提供，
                // fine-grained PAT 不支持 GraphQL（官方明示）；诚实降级为跳网页，不臆造数据。
                val profileUrl = profile?.htmlUrl?.ifBlank { "https://github.com/${profile?.login.orEmpty()}" } ?: "https://github.com"
                FullWidthButton(
                    text = "📊 贡献热力图（网页查看）",
                    onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(profileUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }
                    },
                )
                Text(
                    "热力图数据仅 GitHub GraphQL 提供，当前登录方式不支持；已为您跳转网页查看。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        OutlinedButton(
            onClick = viewModel::signOut,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
            Text("退出登录")
        }
    }
}

@Composable
private fun ProfileHeader(profile: UserProfile) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier.size(72.dp).clip(CircleShape),
                color = MaterialTheme.colorScheme.primaryContainer,
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text =
                            profile.name
                                ?.firstOrNull()
                                ?.uppercaseChar()
                                ?.toString()
                                ?: profile.login
                                    .firstOrNull()
                                    ?.uppercaseChar()
                                    ?.toString()
                                    .orEmpty(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = profile.name ?: profile.login,
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "@${profile.login}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            profile.bio?.let { bio ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
            val meta = listOfNotNull(profile.company, profile.location).joinToString(" · ")
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // v0.10.0 N4：补全个人网站与创建时间（截图对照项）。
            if (!profile.blog.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text("🔗 ${profile.blog}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            profile.createdAt?.let { created ->
                Spacer(Modifier.height(4.dp))
                Text(
                    "创建于：${created.take(10)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                StatItem(value = profile.publicRepos, label = "仓库")
                StatItem(value = profile.followers, label = "关注者")
                StatItem(value = profile.following, label = "关注")
                StatItem(value = profile.publicGists, label = "Gist")
            }
        }
    }
}

@Composable
private fun StatItem(
    value: Int,
    label: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value.toString(), style = MaterialTheme.typography.titleLarge)
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun FullWidthButton(
    text: String,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(text, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Start)
    }
}
