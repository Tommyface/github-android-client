/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.navigation

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import com.gy.githubclient.core.domain.link.GitHubLink
import com.gy.githubclient.core.domain.link.GitHubLinkTarget
import com.gy.githubclient.feature.download.domain.model.DownloadRequest
import com.gy.githubclient.feature.download.domain.usecase.EnqueueDownloadUseCase
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/**
 * GitHub 链接 → 应用内动作（v0.16.0，ghlink.js 的 Native 移植）。
 *
 * 纪律与 githup 一致：
 * - 只有应用里真实存在的页面才进路由；
 * - 站内没有的页面（用户主页/ wiki / trending）明确交给浏览器；
 * - Release 资产 / codeload 直链进入自己的下载队列，而不是丢给浏览器。
 */
@Composable
fun rememberDeepLinkAction(
    navController: NavHostController,
    enqueueDownload: EnqueueDownloadUseCase = koinInject(),
): (GitHubLink) -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(navController, context, enqueueDownload) {
        { link ->
            when (val target = link.target) {
                is GitHubLinkTarget.Repository ->
                    navController.navigate(Route.RepoDetail.route(target.owner, target.repo))

                is GitHubLinkTarget.Files ->
                    navController.navigate(Route.FileBrowser.route(target.owner, target.repo, target.ref))

                is GitHubLinkTarget.Releases ->
                    navController.navigate(Route.ReleaseList.route(target.owner, target.repo))

                is GitHubLinkTarget.Commits ->
                    navController.navigate(Route.Commits.route(target.owner, target.repo))

                is GitHubLinkTarget.Actions ->
                    navController.navigate(Route.Actions.route(target.owner, target.repo))

                GitHubLinkTarget.Notifications -> navController.navigate(Route.Notifications.path)

                GitHubLinkTarget.Gists -> navController.navigate(Route.Gists.path)

                is GitHubLinkTarget.Download ->
                    scope.launch {
                        runCatching {
                            enqueueDownload(
                                DownloadRequest(
                                    url = target.url,
                                    fileName = target.fileName,
                                    suggestedFileName = target.fileName,
                                    repoOwner = target.owner.ifBlank { null },
                                    repoName = target.repo.ifBlank { null },
                                    releaseTag = target.tag,
                                    assetId = 0L,
                                    totalBytes = -1L,
                                    useMirror = true,
                                ),
                            )
                        }.onSuccess {
                            toast(context, "已加入下载队列：${target.fileName}")
                        }.onFailure {
                            toast(context, "下载入队失败：${it.message ?: "未知错误"}")
                        }
                    }

                is GitHubLinkTarget.External -> openBrowser(context, target.url)
                GitHubLinkTarget.Trending -> openBrowser(context, TRENDING_URL)
                GitHubLinkTarget.Home -> openBrowser(context, GITHUB_HOME_URL)
            }
        }
    }
}

/** 启动/分享进来的链接消费：只消费一次，避免重组时重复导航。 */
@Composable
fun DeepLinkHandler(
    link: GitHubLink?,
    openLink: (GitHubLink) -> Unit,
    onConsumed: () -> Unit,
) {
    LaunchedEffect(link) {
        if (link == null) return@LaunchedEffect
        onConsumed()
        openLink(link)
    }
}

private fun openBrowser(
    context: Context,
    url: String,
) {
    runCatching {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.onFailure {
        toast(context, "没有可打开该链接的应用")
    }
}

private fun toast(
    context: Context,
    message: String,
) {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
}

private const val TRENDING_URL = "https://github.com/trending"
private const val GITHUB_HOME_URL = "https://github.com"
