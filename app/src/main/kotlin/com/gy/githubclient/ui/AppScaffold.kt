/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.gy.githubclient.core.presentation.theme.FrostDimens
import com.gy.githubclient.core.presentation.theme.FrostIcon
import com.gy.githubclient.core.presentation.theme.FrostInsets
import com.gy.githubclient.core.presentation.theme.FrostNavBar
import com.gy.githubclient.core.presentation.theme.FrostNavItem
import com.gy.githubclient.core.presentation.theme.FrostTopBar
import com.gy.githubclient.core.presentation.theme.LocalFrostColors
import com.gy.githubclient.core.presentation.theme.LocalFrostContentInsets
import com.gy.githubclient.core.presentation.theme.LocalFrostMotion
import com.gy.githubclient.core.presentation.theme.rememberFrostMotionState
import com.gy.githubclient.core.presentation.theme.trackFrostScroll
import com.gy.githubclient.navigation.Route

/** 顶部占位：通栏页 / 自绘通栏页 / 普通页三种情形。 */
private fun computeTopInset(
    showsTopBar: Boolean,
    selfChrome: Boolean,
    statusTop: Dp,
    barBodyHeight: Dp,
): Dp =
    when {
        showsTopBar -> statusTop + barBodyHeight + FrostDimens.TopBarFade
        selfChrome -> statusTop + FrostDimens.TopBarHeight + FrostDimens.TopBarFade
        else -> statusTop + 8.dp
    }

/** 底部占位：Tab 页需要为浮动导航条留位。 */
private fun computeBottomInset(
    hasTab: Boolean,
    navInset: Dp,
): Dp =
    if (hasTab) {
        FrostDimens.NavBarHeight + FrostDimens.NavBarBottomGap + navInset
    } else {
        navInset + 8.dp
    }

/**
 * 离线提示条（P-200 缺口 3）。
 *
 * 断网时**不阻断操作、不清空界面**——Room 缓存仍可读，只是数据可能是旧的；
 * 因此提示条要说清"这是缓存数据"，而不是弹一个错误。
 */
@Composable
private fun OfflineBanner(modifier: Modifier = Modifier) {
    val frost = LocalFrostColors.current
    Surface(
        modifier = modifier.padding(top = 8.dp),
        color = frost.stateWarning.copy(alpha = 0.16f),
        shape = RoundedCornerShape(10.dp),
    ) {
        Text(
            text = "离线模式 · 显示本地缓存数据",
            style = MaterialTheme.typography.labelMedium,
            color = frost.stateWarning,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/**
 * 底部 Tab 定义。
 *
 * [title] 为空表示该 Tab 不画顶部通栏（内容自己带头部，避免双标题）：
 * 「我的」页沿用原有的大标题布局，其余三个列表页统一走 Frost 通栏。
 */
private data class TabSpec(
    val route: Route,
    val label: String,
    val icon: FrostIcon,
    val title: String?,
    val subtitle: String? = null,
)

private val bottomTabs =
    listOf(
        TabSpec(Route.Home, "仓库", FrostIcon.GRID, "我的仓库", "GitHub · 磨砂玻璃"),
        TabSpec(Route.Discover, "发现", FrostIcon.COMPASS, "发现", "热门仓库 · 最新 Release"),
        TabSpec(Route.Search, "搜索", FrostIcon.SEARCH, "搜索", "仓库 · 代码 · 用户"),
        TabSpec(Route.Profile, "我的", FrostIcon.USER, null),
    )

/** 通栏带副标题时比单行标题高出的量（与 FrostTopBar 内部一致）。 */
private val SubtitleExtraHeight = 12.dp

/**
 * 自己绘制 Frost 通栏的二级页面：外壳不再替它们加内边距，而是把通栏 / 导航占位
 * 通过 [LocalFrostContentInsets] 下发，由页面写进 LazyColumn / Column 的内边距，
 * 于是滚动内容可以穿到玻璃通栏下方（与四个 Tab 页一致）。
 *
 * 未登记的路由继续由外壳统一加 padding，避免内容压到系统栏。
 */
private val selfChromeRoutes: Set<String> =
    setOf(
        Route.RepoDetail.path,
        Route.ReleaseList.path,
        Route.ReleaseDetail.path,
        Route.PermissionAudit.path,
        Route.Downloads.path,
        Route.MirrorSettings.path,
    )

/**
 * 应用外壳（v0.19.0 Frost）：
 * - 顶部通栏自 y=0 起、含状态栏区域，底部 18dp 渐变收尾，无分割线；
 * - 底部使用浮动玻璃导航胶囊，仅 4 个 Tab 页显示；
 * - 全屏只有这 2 个动态模糊层，滚动时通过 [LocalFrostMotion] 统一下发降级信号；
 * - 通过 [LocalFrostContentInsets] 把通栏 / 导航占位下发给页面：
 *   Frost 页面自己把占位写进 LazyColumn 的 contentPadding，内容可以滚到通栏下方；
 *   其余未改造的老页面仍由外壳统一加 padding，避免内容压到系统栏。
 */
@Composable
fun AppScaffold(
    navController: NavHostController,
    content: @Composable () -> Unit,
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val tab = bottomTabs.firstOrNull { it.route.path == currentRoute }
    val isSettings = currentRoute == Route.Settings.path
    val motion = rememberFrostMotionState()
    // 离线态（P-200 缺口 3）：断网时给出提示条，但**不清空界面**——Room 缓存仍可用。
    val online by rememberOnlineState()

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val barTitle = if (isSettings) "设置" else tab?.title
    val showsTopBar = barTitle != null
    val barSubtitle = if (isSettings) null else tab?.subtitle
    val barBodyHeight = FrostDimens.TopBarHeight + if (barSubtitle != null) SubtitleExtraHeight else 0.dp

    // 二级页面自己画通栏（返回键在通栏左侧），外壳只下发占位高度。
    val selfChrome = currentRoute != null && currentRoute in selfChromeRoutes

    val topInset = computeTopInset(showsTopBar, selfChrome, statusTop, barBodyHeight)
    val bottomInset = computeBottomInset(tab != null, navInset)

    // Frost 页面自己消费 LocalFrostContentInsets；老页面由外壳兜底加 padding。
    val consumesInsets = showsTopBar || selfChrome

    CompositionLocalProvider(
        LocalFrostMotion provides motion,
        LocalFrostContentInsets provides FrostInsets(top = topInset, bottom = bottomInset),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .then(
                            if (consumesInsets) {
                                Modifier
                            } else {
                                Modifier.padding(top = topInset, bottom = bottomInset)
                            },
                        ).trackFrostScroll(motion),
            ) {
                content()
            }

            // 离线提示条（P-200 缺口 3）：断网时明确告知"当前是缓存数据"。
            if (!online) {
                OfflineBanner(modifier = Modifier.align(Alignment.TopCenter))
            }

            if (barTitle != null) {
                FrostTopBar(
                    title = barTitle,
                    subtitle = barSubtitle,
                    onBack = if (isSettings) ({ navController.popBackStack() }) else null,
                    largeTitle = false,
                )
            }

            if (tab != null) {
                FrostNavBar(
                    items = bottomTabs.map { FrostNavItem(label = it.label, icon = it.icon) },
                    selectedIndex = bottomTabs.indexOf(tab),
                    onSelect = { index ->
                        val target = bottomTabs[index].route
                        if (target.path != currentRoute) {
                            navController.navigate(target.path) {
                                popUpTo(Route.Home.path) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
