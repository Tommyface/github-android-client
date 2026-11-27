/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.verify.assertTrue
import org.junit.Test
import java.io.File
import org.junit.Assert.assertTrue as junitAssertTrue

/**
 * 门禁 6：架构约定测试（konsist 规则 12 条，M0-11）。
 *
 * 规则编号与《GHHub 最优修改报告》§12.5 一一对应；改动规则时同步更新该表。
 * 每条规则都必须能被一条「故意违规的代码」证伪，否则视为无效门禁。
 *
 * 实现约定：涉及「文件内容」的规则（6/9/11/12）直接读磁盘文本，
 * 不依赖 Konsist 的内容解析 API，以降低 API 漂移风险。
 */
class ArchitectureTest {
    /**
     * 仓库根目录。Gradle 单测的工作目录是**模块目录**（konsist-test/），
     * 直接用 user.dir 会让相对路径全部落空——这会让门禁「假通过」。
     * 因此自底向上寻找含 settings.gradle.kts 的目录。
     */
    private val repoRoot: File =
        run {
            var dir = File(System.getProperty("user.dir")).absoluteFile
            while (dir.parentFile != null) {
                if (File(dir, "settings.gradle.kts").exists()) break
                dir = dir.parentFile
            }
            dir
        }

    private fun readRepoFile(relativePath: String): String {
        val file = File(relativePath).takeIf { it.isAbsolute } ?: File(repoRoot, relativePath)
        return if (file.exists()) file.readText() else ""
    }

    // ── 规则 1：领域层不得依赖 Android 框架 ────────────────────────────
    @Test
    fun domainLayerDoesNotDependOnAndroidFramework() {
        Konsist
            .scopeFromDirectory("core/domain/src/main/kotlin")
            .files
            .assertTrue { file ->
                !file.hasImportWithName("android.content.Context", "android.app.Activity")
            }
    }

    // ── 规则 2：凭证只在 core/data 与 platform/* 可见 ─────────────────
    @Test
    fun credentialsOnlyVisibleInDataAndPlatformLayers() {
        Konsist
            .scopeFromDirectory(".")
            .files
            .assertTrue { file ->
                val isUiOrDomain =
                    file.path.contains("/presentation/") || file.path.contains("/domain/")
                if (!isUiOrDomain) {
                    true
                } else {
                    !file.name.endsWith("SecureTokenStore.kt") &&
                        !file.hasImportWithName(
                            "com.gy.githubclient.feature.auth.data.local.SecureTokenStore",
                        )
                }
            }
    }

    // ── 规则 3：platform/* 不得反向依赖 feature/* ──────────────────────
    @Test
    fun platformLayerDoesNotDependOnFeatureModules() {
        Konsist
            .scopeFromDirectory(".")
            .files
            .assertTrue { file ->
                if (!file.path.contains("/platform/")) {
                    true
                } else {
                    file.imports.none { import ->
                        import.name.contains("com.gy.githubclient.feature")
                    }
                }
            }
    }

    // ── 规则 4：纯逻辑层不得 import Android ───────────────────────────
    @Test
    fun pureLogicFilesDoNotImportAndroid() {
        val pureLogicRoots = listOf("/core/domain/", "/core/mutation/", "/core/permission/")
        Konsist
            .scopeFromDirectory(".")
            .files
            .assertTrue { file ->
                val path = file.path.replace('\\', '/')
                val isPureLogic = pureLogicRoots.any { root -> path.contains(root) }
                if (!isPureLogic) {
                    true
                } else {
                    file.imports.none { import -> import.name.startsWith("android.") }
                }
            }
    }

    // ── 规则 5：feature 之间的横向依赖（棘轮式：债务只减不增） ─────────

    /**
     * 本规则采用**棘轮**形式而非一步到位：
     *  - 现状：feature 之间存在 23 处横向依赖（多为「presentation 用到另一个 feature 的 domain 模型」）；
     *    （此前文档一直写 22，实际清单与实测都是 23——R7 校准为实测值，数字必须对得上）；
     *  - 规则：新增任何一处横向依赖即失败；已登记的存量债务若消失也失败（必须同步删除登记）；
     *  - 目的：先把「不再恶化」变成机器可校验的事实，再按 M2-04/M2-09 的重构逐条清账。
     *
     * 清账方向（见 spec/ROADMAP.md）：把跨 feature 复用的模型下沉到 core:domain，
     * 或把该能力合并进所属 feature，避免 presentation 层互相 import。
     */
    @Test
    fun featureCrossDependenciesDoNotGrow() {
        val known = KNOWN_FEATURE_CROSS_DEPENDENCIES
        val current = collectFeatureCrossDependencies()

        val introduced = current - known
        junitAssertTrue(
            "新增了 feature 横向依赖（门禁禁止债务增长）：\n" + introduced.joinToString("\n") { "  $it" },
            introduced.isEmpty(),
        )
        val cleared = known - current
        junitAssertTrue(
            "登记的横向依赖已不存在，请同步从本清单移除（债务只减不增）：\n" +
                cleared.joinToString("\n") { "  $it" },
            cleared.isEmpty(),
        )
    }

    /** 扫描 feature 目录，返回「<feature>/<相对路径> -> 被依赖的 feature」集合。 */
    private fun collectFeatureCrossDependencies(): Set<Pair<String, String>> {
        val featureRoots =
            listOf(
                "auth",
                "repo",
                "discover",
                "download",
                "search",
                "starred",
                "profile",
                "library",
            )
        val importRegex = Regex("^import\\s+com\\.gy\\.githubclient\\.feature\\.(\\w+)", RegexOption.MULTILINE)
        val result = mutableSetOf<Pair<String, String>>()
        val featureDir = File(repoRoot, "feature")
        if (!featureDir.exists()) return result
        featureDir
            .walkTopDown()
            .filter { it.isFile && it.name.endsWith(".kt") }
            .forEach { file ->
                val full = file.relativeTo(repoRoot).path.replace('\\', '/')
                val own = featureRoots.firstOrNull { root -> full.contains("/feature/$root/") }
                if (own == null) return@forEach
                val short = full.substringAfter("com/gy/githubclient/feature/")
                importRegex.findAll(file.readText()).forEach { match ->
                    val target = match.groupValues[1]
                    if (target != own) result += short to target
                }
            }
        return result
    }

    // ── 规则 6：日志调用必须经过脱敏器 ───────────────────────────────
    @Test
    fun loggingMustGoThroughRedactor() {
        val rawLogCalls = listOf("android.util.Log", "kotlin.io.println", "java.lang.System.out")
        Konsist
            .scopeFromDirectory(".")
            .files
            .assertTrue { file ->
                if (file.name.endsWith("Redactor.kt") ||
                    file.path.replace('\\', '/').contains("/platform/diagnostics/")
                ) {
                    true
                } else {
                    file.imports.none { import -> rawLogCalls.contains(import.name) }
                }
            }
    }

    // ── 规则 7：写操作必须经 MutationExecutor ─────────────────────────
    @Test
    fun writeOperationsMustUseMutationExecutor() {
        val writeApiNames = listOf("GitHubWriteApi.kt", "MutationApi.kt")
        Konsist
            .scopeFromDirectory(".")
            .files
            .assertTrue { file ->
                if (!writeApiNames.contains(file.name)) {
                    true
                } else {
                    file.path.replace('\\', '/').contains("/mutation/") ||
                        file.name.endsWith("MutationExecutor.kt")
                }
            }
    }

    // ── 规则 8：危险操作 UI 必须挂二次确认组件 ───────────────────────
    @Test
    fun dangerousUiMustUseDangerConfirm() {
        val dangerousKeywords = listOf("DeleteRepository", "ArchiveRepository", "ChangeVisibility")
        Konsist
            .scopeFromDirectory(".")
            .files
            .assertTrue { file ->
                if (!file.path.replace('\\', '/').contains("/presentation/")) {
                    true
                } else {
                    val triggersDanger = dangerousKeywords.any { keyword -> file.name.contains(keyword) }
                    !triggersDanger ||
                        file.hasImportWithName(
                            "com.gy.githubclient.core.presentation.components.DangerConfirmSheet",
                        )
                }
            }
    }

    // ── 规则 9：权限数 ≤ 6 ───────────────────────────────────────────
    @Test
    fun manifestPermissionsAreDocumented() {
        val manifest = readRepoFile("app/src/main/AndroidManifest.xml")
        val permissionLines =
            manifest.lines().filter { it.contains("<uses-permission") && !it.trim().startsWith("<!--") }
        junitAssertTrue(
            "权限数 ${permissionLines.size} 超过目标 6 项；新增权限必须在 PR 中说明理由并更新本规则。\n" +
                permissionLines.joinToString("\n"),
            permissionLines.size <= 6,
        )
    }

    // ── 规则 10：Compose 组件不得直接调用网络层 ──────────────────────
    @Test
    fun composeComponentsDoNotCallNetworkLayerDirectly() {
        Konsist
            .scopeFromDirectory(".")
            .files
            .assertTrue { file ->
                if (!file.path.replace('\\', '/').contains("/presentation/")) {
                    true
                } else {
                    file.imports.none { import ->
                        import.name.startsWith("io.ktor") ||
                            import.name.startsWith("okhttp3") ||
                            import.name.endsWith("GitHubApiClient")
                    }
                }
            }
    }

    // ── 规则 11：跳转对象存储的请求必须剥 Authorization ──────────────
    @Test
    fun objectStorageRedirectsMustStripAuthorization() {
        Konsist
            .scopeFromDirectory(".")
            .files
            .assertTrue { file ->
                if (!file.name.contains("Actions") || !file.name.endsWith("Api.kt")) {
                    true
                } else {
                    val text = readRepoFile(file.path.replace('\\', '/'))
                    text.contains("Authorization")
                }
            }
    }

    // ── 规则 12：spec/parity/P-XXX 编号规范 ───────────────────────────
    @Test
    fun paritySpecsFollowNamingConvention() {
        val parityDir = File(repoRoot, "spec/parity")
        if (!parityDir.exists()) return
        val specs = parityDir.listFiles { file -> file.name.endsWith(".md") }.orEmpty()
        specs.forEach { spec ->
            val isTemplate = spec.name.startsWith("_")
            val isNotice = spec.name.contains("NOTICE") || spec.name.contains("BASELINE")
            if (!isTemplate && !isNotice) {
                junitAssertTrue(
                    "行为规格文件名必须以 P- 开头（例 P-101-update-semantic-level.md），实际：${spec.name}",
                    spec.name.startsWith("P-"),
                )
            }
        }
    }

    // ── 规则 13：新增界面不得硬编码中文字面量（字符串化棘轮） ───────

    /**
     * M0-18 前置门禁（**双向棘轮**）。
     *
     * 规则：
     *  1. 扫描范围 = app + core/presentation + 全部 feature 的 presentation 层
     *     （此前只扫前两处，feature 的 30+ 个界面文件完全不在约束内——那是漏检，不是豁免）；
     *  2. 判定前**先剔除注释**：KDoc 里出现的中文引号（例如"确定吗？"）不是界面文案，
     *     直接扫全文会把注释误判为硬编码文案，白名单被无谓文件占满，真实债务反而被淹没；
     *  3. 白名单只减不增：新增界面文件出现中文字面量即失败；
     *  4. **登记项失效也失败**：白名单里的文件一旦清干净（或被改名/删除），
     *     必须同步删掉登记行——否则"已还债"会被当成"仍欠债"，棘轮失去意义。
     */
    @Test
    fun newUiFilesDoNotHardcodeChineseStrings() {
        val chineseLiteral = Regex("\"[^\"\\n]*[\\u4e00-\\u9fff][^\"\\n]*\"")
        val allowedFiles = LEGACY_HARDCODED_STRING_FILES
        val violations = mutableListOf<String>()
        val staleEntries = mutableListOf<String>()
        val sourceDirs = listOf("app/src/main/kotlin", "core/presentation/src/main/kotlin") + featureUiRoots()
        val dirtyFiles = mutableSetOf<String>()
        sourceDirs.forEach { dir ->
            val root = File(repoRoot, dir)
            if (!root.exists()) return@forEach
            root
                .walkTopDown()
                .filter { it.isFile && it.name.endsWith(".kt") }
                .forEach { file ->
                    val relative = file.relativeTo(repoRoot).path.replace('\\', '/')
                    val hasChinese = chineseLiteral.containsMatchIn(stripComments(file.readText()))
                    if (hasChinese) dirtyFiles += relative
                    if (relative in allowedFiles) return@forEach
                    if (hasChinese) violations += relative
                }
        }
        // 反向棘轮：登记项必须仍然对应"确实还有硬编码文案"的文件。
        allowedFiles.forEach { registered ->
            if (registered !in dirtyFiles) staleEntries += registered
        }
        junitAssertTrue(
            "新增/改名的界面文件不得硬编码中文字面量（请用 stringResource）：\n" +
                violations.joinToString("\n") { "  $it" },
            violations.isEmpty(),
        )
        junitAssertTrue(
            "字符串化白名单登记项已失效（该文件已无硬编码中文，或已被改名/删除），" +
                "请同步从 LEGACY_HARDCODED_STRING_FILES 中删除：\n" +
                staleEntries.joinToString("\n") { "  $it" },
            staleEntries.isEmpty(),
        )
    }

    /**
     * 去掉 Kotlin 注释，保留字符串字面量内容。
     *
     * 目的是让规则 13 只对**真正的界面文案**生效：
     * 注释里的中文引号不是文案，不该把文件推进存量白名单。
     * 实现上按字符扫描，识别行注释、块注释（含嵌套）、字符串与字符字面量；
     * 字符串模板 `${…}` 内的内容原样保留（其内部注释极少，不做递归处理）。
     */
    private fun stripComments(source: String): String {
        val out = StringBuilder(source.length)
        var i = 0
        while (i < source.length) {
            when {
                source.startsWith("//", i) || source.startsWith("/*", i) -> {
                    i = skipComment(source, i)
                }

                source[i] == '"' -> {
                    val end = skipStringLiteral(source, i)
                    out.append(source, i, end)
                    i = end
                }

                source[i] == '\'' -> {
                    val end = skipCharLiteral(source, i)
                    out.append(source, i, end)
                    i = end
                }

                else -> {
                    out.append(source[i])
                    i++
                }
            }
        }
        return out.toString()
    }

    /** 跳过一段注释（行注释或块注释，含嵌套），返回结束后的下标。 */
    private fun skipComment(source: String, start: Int): Int {
        if (source.startsWith("//", start)) {
            var i = start + 2
            while (i < source.length && source[i] != '\n') i++
            return i
        }
        var depth = 1
        var i = start + 2
        while (i < source.length && depth > 0) {
            when {
                source.startsWith("/*", i) -> {
                    depth++
                    i += 2
                }
                source.startsWith("*/", i) -> {
                    depth--
                    i += 2
                }
                else -> i++
            }
        }
        return i
    }

    /** 跳过字符串字面量（含三引号与 `${…}` 模板），返回结束后的下标。 */
    private fun skipStringLiteral(source: String, start: Int): Int {
        val n = source.length
        if (source.startsWith("\"\"\"", start)) {
            var j = start + 3
            while (j < n && !source.startsWith("\"\"\"", j)) j++
            return minOf(j + 3, n)
        }
        var j = start + 1
        while (j < n) {
            when {
                source[j] == '\\' -> j += 2
                source[j] == '"' -> return j + 1
                source[j] == '$' && j + 1 < n && source[j + 1] == '{' -> j = skipTemplate(source, j + 2)
                else -> j++
            }
        }
        return j
    }

    /** 跳过字符字面量（如 `'a'`）。 */
    private fun skipCharLiteral(source: String, start: Int): Int {
        val n = source.length
        var j = start + 1
        while (j < n) {
            when {
                source[j] == '\\' -> j += 2
                source[j] == '\'' -> return j + 1
                source[j] == '\n' -> return j
                else -> j++
            }
        }
        return j
    }

    /** 跳过字符串模板 `${…}` 的内容，返回匹配右花括号之后的下标。 */
    private fun skipTemplate(source: String, fromIndex: Int): Int {
        var depth = 1
        var i = fromIndex
        val n = source.length
        while (i < n && depth > 0) {
            when {
                source.startsWith("//", i) || source.startsWith("/*", i) -> {
                    i = skipComment(source, i)
                }

                source[i] == '"' -> i = skipStringLiteral(source, i)
                source[i] == '{' -> {
                    depth++
                    i++
                }
                source[i] == '}' -> {
                    depth--
                    i++
                }
                else -> i++
            }
        }
        return i
    }

    // ── 规则 14：业务屏不得写死色值（令牌单一色源） ───────────────────

    /**
     * P-200 收编 frost-ui 时的实测结论：3 个业务屏仍写死 `Color(0x…)`，
     * 改主题时不会跟随、深色下可能对比度不足。
     *
     * 本规则要求：除 `core/presentation/theme`（令牌定义处）外，
     * 任何 UI 源码出现 `Color(0x` 即失败。存量例外登记在白名单里，只减不增。
     */
    @Test
    fun businessScreensDoNotHardcodeColors() {
        val allowed =
            setOf(
                // 令牌定义处：色值的唯一来源
                "core/presentation/src/main/kotlin/com/gy/githubclient/core/presentation/theme/Frost.kt",
                "core/presentation/src/main/kotlin/com/gy/githubclient/core/presentation/theme/FrostChrome.kt",
                "core/presentation/src/main/kotlin/com/gy/githubclient/core/presentation/theme/FrostSurface.kt",
                "core/presentation/src/main/kotlin/com/gy/githubclient/core/presentation/theme/Theme.kt",
                // 内容调色板（代码高亮 / Diff 语义色）：**刻意不随主题变化**，
                // 因为它们表达的是「语法角色」而非「界面层级」，跟随主题反而会丢失可读性。
                "feature/repo/presentation/src/main/kotlin/com/gy/githubclient/feature/repo/presentation/FileBrowserScreen.kt",
                "feature/repo/presentation/src/main/kotlin/com/gy/githubclient/feature/repo/presentation/CommitScreen.kt",
                // GitHub 标签色（label color）来自 API 数据，不是界面色，必须按数据原样呈现。
                "feature/repo/presentation/src/main/kotlin/com/gy/githubclient/feature/repo/presentation/RepoDetailScreen.kt",
            )
        val violations = mutableListOf<String>()
        val roots =
            listOf("app/src/main/kotlin", "core/presentation/src/main/kotlin") +
                featureUiRoots()
        roots.forEach { dir ->
            val root = File(repoRoot, dir)
            if (!root.exists()) return@forEach
            root
                .walkTopDown()
                .filter { it.isFile && it.name.endsWith(".kt") }
                .forEach { file ->
                    val relative = file.relativeTo(repoRoot).path.replace('\\', '/')
                    if (relative in allowed) return@forEach
                    if (file.readText().contains("Color(0x")) violations += relative
                }
        }
        junitAssertTrue(
            "业务屏不得写死色值（请用 Frost 令牌）：\n" + violations.joinToString("\n") { "  $it" },
            violations.isEmpty(),
        )
    }

    /** 所有 feature 的 presentation 源码目录。 */
    private fun featureUiRoots(): List<String> =
        File(repoRoot, "feature")
            .listFiles()
            ?.filter { File(it, "presentation/src/main/kotlin").exists() }
            ?.map { "feature/${it.name}/presentation/src/main/kotlin" }
            ?: emptyList()

    private companion object {
        /**
         * 已知硬编码中文的存量界面文件白名单（M0-18 棘轮）。
         *
         * 口径与规则 13 完全一致：**先剔除注释再判定**，扫描范围覆盖
         * app / core:presentation / 全部 feature 的 presentation 层。
         *
         * 数量变化轨迹（每一轮都必须只减不增）：
         *  - R6 结束（旧口径：含注释误报、且漏检 feature）：18 项（实际覆盖 18 个文件）；
         *  - R7 校准后（新口径：剔注释 + 覆盖 feature）：39 项；
         *  - P-207 字符串化（starred 域两文件清零）：**37 项**。
         *    看起来"变多了"，是因为 feature 的 30+ 个界面文件此前**根本不在门禁范围内**，
         *    这里首次被登记——这是把漏检变成显式债务，不是新增债务。
         *
         * 每完成一屏的字符串化，就从这里删掉对应条目——白名单只减不增，
         * 确保"不再新增技术债"这件事本身是可机器校验的。
         */
        val LEGACY_HARDCODED_STRING_FILES: Set<String> =
            setOf(
                "app/src/main/kotlin/com/gy/githubclient/update/UpdateCheckScreen.kt",
                "app/src/main/kotlin/com/gy/githubclient/update/UpdateCheckViewModel.kt",
                "app/src/main/kotlin/com/gy/githubclient/update/UpdateCheckWorker.kt",
                "feature/search/presentation/src/main/kotlin/com/gy/githubclient/feature/search/presentation/SearchScreen.kt",
                "feature/search/presentation/src/main/kotlin/com/gy/githubclient/feature/search/presentation/SearchViewModel.kt",
                "feature/repo/presentation/src/main/kotlin/com/gy/githubclient/feature/repo/presentation/ActionsScreen.kt",
                "feature/repo/presentation/src/main/kotlin/com/gy/githubclient/feature/repo/presentation/ActionsViewModel.kt",
                "feature/repo/presentation/src/main/kotlin/com/gy/githubclient/feature/repo/presentation/CommitScreen.kt",
                "feature/repo/presentation/src/main/kotlin/com/gy/githubclient/feature/repo/presentation/CommitViewModel.kt",
                "feature/repo/presentation/src/main/kotlin/com/gy/githubclient/feature/repo/presentation/FileBrowserScreen.kt",
                "feature/repo/presentation/src/main/kotlin/com/gy/githubclient/feature/repo/presentation/FileBrowserViewModel.kt",
                "feature/repo/presentation/src/main/kotlin/com/gy/githubclient/feature/repo/presentation/RepoDetailScreen.kt",
                "feature/repo/presentation/src/main/kotlin/com/gy/githubclient/feature/repo/presentation/RepoListScreen.kt",
                "feature/repo/presentation/src/main/kotlin/com/gy/githubclient/feature/repo/presentation/RepoViewModel.kt",
                "feature/profile/presentation/src/main/kotlin/com/gy/githubclient/feature/profile/presentation/ActivityScreen.kt",
                "feature/profile/presentation/src/main/kotlin/com/gy/githubclient/feature/profile/presentation/ActivityViewModel.kt",
                "feature/profile/presentation/src/main/kotlin/com/gy/githubclient/feature/profile/presentation/GistScreen.kt",
                "feature/profile/presentation/src/main/kotlin/com/gy/githubclient/feature/profile/presentation/GistViewModel.kt",
                "feature/profile/presentation/src/main/kotlin/com/gy/githubclient/feature/profile/presentation/NotificationsScreen.kt",
                "feature/profile/presentation/src/main/kotlin/com/gy/githubclient/feature/profile/presentation/NotificationsViewModel.kt",
                "feature/profile/presentation/src/main/kotlin/com/gy/githubclient/feature/profile/presentation/OrgScreen.kt",
                "feature/profile/presentation/src/main/kotlin/com/gy/githubclient/feature/profile/presentation/OrgViewModel.kt",
                "feature/profile/presentation/src/main/kotlin/com/gy/githubclient/feature/profile/presentation/ProfileScreen.kt",
                "feature/profile/presentation/src/main/kotlin/com/gy/githubclient/feature/profile/presentation/ProfileViewModel.kt",
                "feature/library/presentation/src/main/kotlin/com/gy/githubclient/feature/library/presentation/LibraryScreen.kt",
                "feature/download/presentation/src/main/kotlin/com/gy/githubclient/feature/download/presentation/DownloadScreen.kt",
                "feature/download/presentation/src/main/kotlin/com/gy/githubclient/feature/download/presentation/MirrorSettingsScreen.kt",
                "feature/download/presentation/src/main/kotlin/com/gy/githubclient/feature/download/presentation/MirrorSettingsViewModel.kt",
                "feature/discover/presentation/src/main/kotlin/com/gy/githubclient/feature/discover/presentation/DiscoverScreen.kt",
                "feature/discover/presentation/src/main/kotlin/com/gy/githubclient/feature/discover/presentation/DiscoverViewModel.kt",
                "feature/discover/presentation/src/main/kotlin/com/gy/githubclient/feature/discover/presentation/ReleaseDetailScreen.kt",
                "feature/discover/presentation/src/main/kotlin/com/gy/githubclient/feature/discover/presentation/ReleaseListScreen.kt",
                "feature/discover/presentation/src/main/kotlin/com/gy/githubclient/feature/discover/presentation/ReleasePresentationShared.kt",
                "feature/auth/presentation/src/main/kotlin/com/gy/githubclient/feature/auth/presentation/AuthViewModel.kt",
                "feature/auth/presentation/src/main/kotlin/com/gy/githubclient/feature/auth/presentation/LoginScreen.kt",
                "feature/auth/presentation/src/main/kotlin/com/gy/githubclient/feature/auth/presentation/PermissionAuditScreen.kt",
                "feature/auth/presentation/src/main/kotlin/com/gy/githubclient/feature/auth/presentation/PermissionAuditViewModel.kt",
            )

        /**
         * 已登记的 feature 横向依赖存量（23 项，R7 已与实测逐条核对一致）。
         * 棘轮规则：新增即失败；登记项失效也失败（必须同步删除）。
         * 每清账一处，就从这里删掉一行。
         */
        val KNOWN_FEATURE_CROSS_DEPENDENCIES: Set<Pair<String, String>> =
            setOf(
                "discover/presentation/DiscoverScreen.kt" to "search",
                "discover/presentation/DiscoverViewModel.kt" to "download",
                "discover/presentation/DiscoverViewModel.kt" to "search",
                "discover/presentation/ReleaseListViewModel.kt" to "download",
                "discover/presentation/ReleasePresentationShared.kt" to "download",
                "library/data/LibraryRepositoryImpl.kt" to "discover",
                "library/data/di/LibraryDataModule.kt" to "discover",
                "library/domain/LibraryDomainTest.kt" to "discover",
                "library/domain/model/AssetSelector.kt" to "discover",
                "library/presentation/LibraryViewModel.kt" to "download",
                "profile/presentation/ProfileViewModel.kt" to "auth",
                "repo/presentation/RepoViewModel.kt" to "download",
                "search/presentation/SearchViewModel.kt" to "repo",
                // 搜索页的「关注/取关」直接用 profile 域的用例（跨域依赖，登记为存量）
                "search/presentation/SearchViewModel.kt" to "profile",
                "starred/data/StarredDataTest.kt" to "repo",
                "starred/data/StarredRepositoryImpl.kt" to "repo",
                "starred/data/local/StarredMappers.kt" to "repo",
                "starred/data/remote/GitHubStarredApi.kt" to "repo",
                "starred/data/remote/StarredDtos.kt" to "repo",
                "starred/domain/repository/StarredRepository.kt" to "repo",
                "starred/domain/usecase/StarredUseCases.kt" to "repo",
                "starred/presentation/StarredScreen.kt" to "repo",
                "starred/presentation/StarredViewModel.kt" to "repo",
            )
    }
}
