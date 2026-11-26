/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.gy.githubclient.core.domain.link.GitHubLink
import com.gy.githubclient.feature.auth.presentation.PermissionAuditScreen
import com.gy.githubclient.feature.discover.presentation.DiscoverScreen
import com.gy.githubclient.feature.discover.presentation.ReleaseDetailScreen
import com.gy.githubclient.feature.discover.presentation.ReleaseListScreen
import com.gy.githubclient.feature.download.presentation.DownloadScreen
import com.gy.githubclient.feature.download.presentation.MirrorSettingsScreen
import com.gy.githubclient.feature.library.presentation.LibraryScreen
import com.gy.githubclient.feature.profile.presentation.ActivityScreen
import com.gy.githubclient.feature.profile.presentation.GistScreen
import com.gy.githubclient.feature.profile.presentation.OrgScreen
import com.gy.githubclient.feature.profile.presentation.ProfileScreen
import com.gy.githubclient.feature.repo.presentation.ActionsScreen
import com.gy.githubclient.feature.repo.presentation.CommitScreen
import com.gy.githubclient.feature.repo.presentation.FileBrowserScreen
import com.gy.githubclient.feature.repo.presentation.RepoDetailScreen
import com.gy.githubclient.feature.repo.presentation.RepoListScreen
import com.gy.githubclient.feature.search.presentation.SearchScreen
import com.gy.githubclient.feature.starred.presentation.StarredScreen
import com.gy.githubclient.settings.SettingsScreen

/** 类型安全路由。P3 阶段各 feature 在此注册自己的路由。 */
sealed class Route(
    val path: String,
) {
    data object Home : Route("home")

    data object Discover : Route("discover")

    data object Search : Route("search")

    data object Profile : Route("profile")

    data object Downloads : Route("downloads")

    data object MirrorSettings : Route("mirror_settings")

    data object PermissionAudit : Route("permission_audit")

    data object Starred : Route("starred")

    data object UpdateCheck : Route("update_check")

    data object Notifications : Route("notifications")

    /** 应用库（v0.10.0 N1）：追踪仓库 Release + 批量更新。 */
    data object Library : Route("library")

    /** 个人动态（v0.10.0 N4）：公开事件流。 */
    data object Activity : Route("activity")

    /** 组织信息只读（v0.15.0 A5）。 */
    data object Org : Route("org")

    /** Gist 浏览只读（v0.15.0 A6）。 */
    data object Gists : Route("gists")

    /** 外观与缓存设置（v0.16.0）。 */
    data object Settings : Route("settings")

    data object RepoDetail : Route("repo_detail/{owner}/{name}") {
        const val ARG_OWNER = "owner"
        const val ARG_NAME = "name"

        fun route(
            owner: String,
            name: String,
        ) = "repo_detail/$owner/$name"
    }

    data object ReleaseDetail : Route("release_detail/{owner}/{name}") {
        const val ARG_OWNER = "owner"
        const val ARG_NAME = "name"

        fun route(
            owner: String,
            name: String,
        ) = "release_detail/$owner/$name"
    }

    /** 任意仓库的多版本 Release / Tag 列表（v0.8.0 M15）。 */
    data object ReleaseList : Route("release_list/{owner}/{name}") {
        const val ARG_OWNER = "owner"
        const val ARG_NAME = "name"

        fun route(
            owner: String,
            name: String,
        ) = "release_list/$owner/$name"
    }

    /** 仓库文件浏览器（v0.9.0 M18）：ref 为分支/tag/sha。 */
    data object FileBrowser : Route("file_browser/{owner}/{name}/{ref}") {
        const val ARG_OWNER = "owner"
        const val ARG_NAME = "name"
        const val ARG_REF = "ref"

        fun route(
            owner: String,
            name: String,
            ref: String,
        ) = "file_browser/$owner/$name/$ref"
    }

    /** 提交历史与 Diff（v0.12.0 N10）。 */
    data object Commits : Route("commits/{owner}/{name}") {
        const val ARG_OWNER = "owner"
        const val ARG_NAME = "name"

        fun route(
            owner: String,
            name: String,
        ) = "commits/$owner/$name"
    }

    /** Actions Run 只读列表（v0.14.0 A3）。 */
    data object Actions : Route("actions/{owner}/{name}") {
        const val ARG_OWNER = "owner"
        const val ARG_NAME = "name"

        fun route(
            owner: String,
            name: String,
        ) = "actions/$owner/$name"
    }
}

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String = Route.Home.path,
    startLink: GitHubLink? = null,
    onLinkConsumed: () -> Unit = {},
) {
    val openLink = rememberDeepLinkAction(navController)
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Route.Home.path) {
            RepoListScreen(
                onOpenDetail = { owner, name ->
                    navController.navigate(Route.RepoDetail.route(owner, name))
                },
            )
        }
        composable(
            Route.RepoDetail.path,
            arguments =
                listOf(
                    navArgument(Route.RepoDetail.ARG_OWNER) { type = NavType.StringType },
                    navArgument(Route.RepoDetail.ARG_NAME) { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString(Route.RepoDetail.ARG_OWNER).orEmpty()
            val name = backStackEntry.arguments?.getString(Route.RepoDetail.ARG_NAME).orEmpty()
            RepoDetailScreen(
                owner = owner,
                name = name,
                onBack = { navController.popBackStack() },
                onOpenReleases = { navController.navigate(Route.ReleaseList.route(owner, name)) },
                onOpenFiles = { ref -> navController.navigate(Route.FileBrowser.route(owner, name, ref)) },
                onOpenCommits = { navController.navigate(Route.Commits.route(owner, name)) },
                onOpenActions = { navController.navigate(Route.Actions.route(owner, name)) },
            )
        }
        composable(
            Route.Commits.path,
            arguments =
                listOf(
                    navArgument(Route.Commits.ARG_OWNER) { type = NavType.StringType },
                    navArgument(Route.Commits.ARG_NAME) { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString(Route.Commits.ARG_OWNER).orEmpty()
            val name = backStackEntry.arguments?.getString(Route.Commits.ARG_NAME).orEmpty()
            CommitScreen(
                owner = owner,
                name = name,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Route.Actions.path,
            arguments =
                listOf(
                    navArgument(Route.Actions.ARG_OWNER) { type = NavType.StringType },
                    navArgument(Route.Actions.ARG_NAME) { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString(Route.Actions.ARG_OWNER).orEmpty()
            val name = backStackEntry.arguments?.getString(Route.Actions.ARG_NAME).orEmpty()
            ActionsScreen(
                owner = owner,
                name = name,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Route.FileBrowser.path,
            arguments =
                listOf(
                    navArgument(Route.FileBrowser.ARG_OWNER) { type = NavType.StringType },
                    navArgument(Route.FileBrowser.ARG_NAME) { type = NavType.StringType },
                    navArgument(Route.FileBrowser.ARG_REF) { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString(Route.FileBrowser.ARG_OWNER).orEmpty()
            val name = backStackEntry.arguments?.getString(Route.FileBrowser.ARG_NAME).orEmpty()
            val ref = backStackEntry.arguments?.getString(Route.FileBrowser.ARG_REF).orEmpty()
            FileBrowserScreen(
                owner = owner,
                name = name,
                ref = ref,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Route.ReleaseList.path,
            arguments =
                listOf(
                    navArgument(Route.ReleaseList.ARG_OWNER) { type = NavType.StringType },
                    navArgument(Route.ReleaseList.ARG_NAME) { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString(Route.ReleaseList.ARG_OWNER).orEmpty()
            val name = backStackEntry.arguments?.getString(Route.ReleaseList.ARG_NAME).orEmpty()
            ReleaseListScreen(
                owner = owner,
                name = name,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Route.Discover.path) {
            DiscoverScreen(
                onOpenRelease = { owner, name ->
                    navController.navigate(Route.ReleaseDetail.route(owner, name))
                },
            )
        }
        composable(
            Route.ReleaseDetail.path,
            arguments =
                listOf(
                    navArgument(Route.ReleaseDetail.ARG_OWNER) { type = NavType.StringType },
                    navArgument(Route.ReleaseDetail.ARG_NAME) { type = NavType.StringType },
                ),
        ) { backStackEntry ->
            val owner = backStackEntry.arguments?.getString(Route.ReleaseDetail.ARG_OWNER).orEmpty()
            val name = backStackEntry.arguments?.getString(Route.ReleaseDetail.ARG_NAME).orEmpty()
            ReleaseDetailScreen(
                owner = owner,
                name = name,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Route.Search.path) {
            // 搜索页的"结果点击跳转"与"深链跳转"依赖 frost-ui 的关注/收藏写能力
            // （core:permission + profile/repo 写用例），待写操作里程碑一并接入；
            // 本轮先保持原行为，不阻塞其余外壳改造。
            SearchScreen()
        }
        composable(Route.Profile.path) {
            ProfileScreen(
                onOpenStarred = { navController.navigate(Route.Starred.path) },
                onOpenDownloads = { navController.navigate(Route.Downloads.path) },
                onOpenMirrorSettings = { navController.navigate(Route.MirrorSettings.path) },
                onOpenPermissionAudit = { navController.navigate(Route.PermissionAudit.path) },
                onOpenNotifications = { navController.navigate(Route.Notifications.path) },
                onOpenUpdateCheck = { navController.navigate(Route.UpdateCheck.path) },
                onOpenLibrary = { navController.navigate(Route.Library.path) },
                onOpenActivity = { navController.navigate(Route.Activity.path) },
                onOpenOrg = { navController.navigate(Route.Org.path) },
                onOpenGists = { navController.navigate(Route.Gists.path) },
                onOpenSettings = { navController.navigate(Route.Settings.path) },
            )
        }
        composable(Route.Settings.path) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.Activity.path) {
            ActivityScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.Org.path) {
            OrgScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.Gists.path) {
            GistScreen(onBack = { navController.popBackStack() })
        }
        composable(Route.Library.path) {
            LibraryScreen(
                onOpenRelease = { owner, name ->
                    navController.navigate(Route.ReleaseList.route(owner, name))
                },
            )
        }
        composable(Route.Starred.path) {
            StarredScreen(
                onOpenDetail = { owner, name ->
                    navController.navigate(Route.RepoDetail.route(owner, name))
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Route.Downloads.path) {
            DownloadScreen(
                onBack = { navController.popBackStack() },
                onOpenMirrorSettings = { navController.navigate(Route.MirrorSettings.path) },
            )
        }
        composable(Route.MirrorSettings.path) {
            MirrorSettingsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Route.PermissionAudit.path) {
            PermissionAuditScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Route.UpdateCheck.path) {
            com.gy.githubclient.update.UpdateCheckScreen(
                onBack = { navController.popBackStack() },
                onOpenDownloads = { navController.navigate(Route.Downloads.path) },
            )
        }
        composable(Route.Notifications.path) {
            com.gy.githubclient.feature.profile.presentation.NotificationsScreen(
                onBack = { navController.popBackStack() },
            )
        }
    }

    // 启动/分享链接在导航图就绪后消费；同一条链接只会触发一次。
    DeepLinkHandler(link = startLink, openLink = openLink, onConsumed = onLinkConsumed)
}
