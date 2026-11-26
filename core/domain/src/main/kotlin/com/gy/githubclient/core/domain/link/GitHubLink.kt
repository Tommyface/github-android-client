/*
 * Copyright (c) 2026 GitHub Android Client
 * Licensed under the Apache License, Version 2.0
 */

package com.gy.githubclient.core.domain.link

/**
 * 一条 GitHub 链接在本 App 内可以被送到哪里。
 *
 * 设计原则来自 githup 的 ghlink.js：
 * 1. 认不出来就返回 null，绝不猜测 —— 猜错会把用户送到 404 或无关页面；
 * 2. 站内有的页面才进路由，没有的明确标成 [External]，交给浏览器；
 * 3. Release 资产 / 源码包直链是「下载」而不是「页面」，交给下载通道。
 */
sealed interface GitHubLinkTarget {
    /** 仓库详情页。 */
    data class Repository(
        val owner: String,
        val repo: String,
    ) : GitHubLinkTarget

    /** 文件浏览页（只定位到 ref 根目录；App 暂不支持路径级深链）。 */
    data class Files(
        val owner: String,
        val repo: String,
        val ref: String,
    ) : GitHubLinkTarget

    /** Release 列表；[tag] 非空时表示链接指向某个具体发布。 */
    data class Releases(
        val owner: String,
        val repo: String,
        val tag: String? = null,
    ) : GitHubLinkTarget

    /** 提交历史；[ref] 非空时表示具体 commit / 分支。 */
    data class Commits(
        val owner: String,
        val repo: String,
        val ref: String? = null,
    ) : GitHubLinkTarget

    /** Actions 运行列表。 */
    data class Actions(
        val owner: String,
        val repo: String,
    ) : GitHubLinkTarget

    /** 通知中心。 */
    data object Notifications : GitHubLinkTarget

    /** 探索 / Trending。 */
    data object Trending : GitHubLinkTarget

    /** 当前用户 Gist 列表。 */
    data object Gists : GitHubLinkTarget

    /** 首页。 */
    data object Home : GitHubLinkTarget

    /** 可下载的直链（Release 资产、codeload 源码包）。 */
    data class Download(
        val url: String,
        val fileName: String,
        val owner: String = "",
        val repo: String = "",
        val tag: String? = null,
    ) : GitHubLinkTarget

    /** App 没有对应页面，交给浏览器。 */
    data class External(
        val url: String,
    ) : GitHubLinkTarget
}

/** 解析结果：目标页 + 原始链接（用于展示与兜底）。 */
data class GitHubLink(
    val target: GitHubLinkTarget,
    val sourceUrl: String,
)

/**
 * 把 GitHub 链接翻译成 App 内目标（纯字符串处理，无网络、无 Android 依赖）。
 *
 * 支持：
 * - `https://github.com/owner/repo`（含 /tree、/blob、/releases、/commits、/actions 等）
 * - `codeload.github.com` 源码包直链
 * - `githubclient://repo|release/owner/repo` 自定义 scheme
 * - 分享文本中夹带一条 GitHub 链接（[parseFromText]）
 */
object GitHubLinkParser {
    private val FULL_URL =
        Regex(
            """^(?:https?://)?(?:www\.)?(github\.com|gist\.github\.com|raw\.githubusercontent\.com|codeload\.github\.com)(/[^?#\s]*)?(?:[?#].*)?$""",
            RegexOption.IGNORE_CASE,
        )

    /** 分享文本只认显式带协议的链接，避免把「readme github.com/x」这类描述误判成链接。 */
    private val ANY_GITHUB_URL =
        Regex(
            """https?://(?:www\.)?(?:github\.com|codeload\.github\.com)/[^\s<>"']+""",
            RegexOption.IGNORE_CASE,
        )

    private val CUSTOM_SCHEME =
        Regex(
            """^githubclient://(repo|release)/([^/?#\s]+)/([^/?#\s]+)(?:[/?#].*)?$""",
            RegexOption.IGNORE_CASE,
        )

    private val HEX_LIKE = Regex("^[0-9a-f]{20,40}$", RegexOption.IGNORE_CASE)

    private val RESERVED_TOP =
        setOf(
            "settings",
            "notifications",
            "explore",
            "trending",
            "issues",
            "pulls",
            "search",
            "login",
            "join",
            "signup",
            "marketplace",
            "sponsors",
            "collections",
            "events",
            "apps",
            "about",
            "pricing",
            "security",
            "features",
            "solutions",
            "enterprise",
            "readme",
            "topics",
            "orgs",
            "gists",
            "new",
            "account",
            "sessions",
            "organizations",
            "plans",
            "contact",
            "site",
        )

    /** 可直接落到仓库详情页的二级 tab（详情页已有入口/信息）。 */
    private val REPO_TABS =
        setOf(
            "issues",
            "pulls",
            "actions",
            "releases",
            "commits",
            "contributors",
            "branches",
            "tags",
            "settings",
            "stargazers",
            "watchers",
            "forks",
            "milestones",
            "collaborators",
        )

    /** 严格解析：整段文本必须就是一条 GitHub 链接（搜索框场景）。 */
    fun parse(input: String): GitHubLink? {
        val text = normalize(input)
        if (text.isEmpty()) return null
        customScheme(text)?.let { return it }
        val match = FULL_URL.matchEntire(text) ?: return null
        val host = match.groupValues[1].lowercase()
        val rawPath = match.groupValues[2]
        return parseHost(host, rawPath)
    }

    /** 宽松解析：从分享文本中提取第一条 GitHub 链接；没有就返回 null。 */
    fun parseFromText(input: String): GitHubLink? {
        parse(input)?.let { return it }
        val url = ANY_GITHUB_URL.find(input)?.value?.trimEnd('.', ',', ';', ':', '!', '?', ')', ']', '}') ?: return null
        return parse(url)
    }

    private fun customScheme(text: String): GitHubLink? {
        val match = CUSTOM_SCHEME.matchEntire(text) ?: return null
        val kind = match.groupValues[1].lowercase()
        val owner = decodeSegment(match.groupValues[2])
        val repo = decodeSegment(match.groupValues[3])
        if (owner.isBlank() || repo.isBlank()) return null
        val target =
            if (kind == "repo") {
                GitHubLinkTarget.Repository(owner, repo)
            } else {
                GitHubLinkTarget.Releases(owner, repo)
            }
        return GitHubLink(target, text)
    }

    private fun parseHost(
        host: String,
        rawPath: String,
    ): GitHubLink? {
        val rawSegments = rawPath.split('/').filter { it.isNotEmpty() }
        val segments = rawSegments.map(::decodeSegment)
        val sourceUrl = "https://$host$rawPath"
        return when (host) {
            "github.com" -> parseGitHub(segments, rawSegments, sourceUrl)
            "codeload.github.com" -> GitHubLink(parseCodeload(segments, sourceUrl), sourceUrl)
            "gist.github.com" -> {
                val gistId = segments.firstOrNull { HEX_LIKE.matches(it) }
                if (gistId == null) {
                    GitHubLink(GitHubLinkTarget.External(sourceUrl), sourceUrl)
                } else {
                    // 当前 App 只能列出「我的 Gist」，没有任意 gist 详情页，不硬塞。
                    GitHubLink(GitHubLinkTarget.External("https://gist.github.com/$gistId"), sourceUrl)
                }
            }

            else -> GitHubLink(GitHubLinkTarget.External(sourceUrl), sourceUrl)
        }
    }

    private fun parseGitHub(
        segments: List<String>,
        rawSegments: List<String>,
        sourceUrl: String,
    ): GitHubLink? {
        if (segments.isEmpty()) return GitHubLink(GitHubLinkTarget.Home, sourceUrl)
        parseReserved(segments[0].lowercase(), sourceUrl)?.let { return GitHubLink(it, sourceUrl) }

        val owner = segments[0]
        if (segments.size == 1) {
            // 没有任意用户主页路由：交给浏览器，绝不落到一个不存在的仓库。
            return GitHubLink(GitHubLinkTarget.External("https://github.com/$owner"), sourceUrl)
        }
        val repo = segments[1]
        if (segments.size == 2) {
            return GitHubLink(GitHubLinkTarget.Repository(owner, repo), sourceUrl)
        }
        return GitHubLink(parseSubPath(segments, rawSegments, sourceUrl), sourceUrl)
    }

    /** 保留路径（通知/探索/Gist 等）与「App 没有页面」的顶部分支。 */
    private fun parseReserved(
        top: String,
        sourceUrl: String,
    ): GitHubLinkTarget? =
        when (top) {
            "notifications" -> GitHubLinkTarget.Notifications
            "explore", "trending" -> GitHubLinkTarget.Trending
            "gists" -> GitHubLinkTarget.Gists
            in RESERVED_TOP -> GitHubLinkTarget.External(sourceUrl)
            else -> null
        }

    /** 仓库下的二级路径：文件、提交、Actions、Release 等。 */
    private fun parseSubPath(
        segments: List<String>,
        rawSegments: List<String>,
        sourceUrl: String,
    ): GitHubLinkTarget {
        val owner = segments[0]
        val repo = segments[1]
        val sub = segments[2].lowercase()
        val rest = segments.drop(3)
        val restRaw = rawSegments.drop(3)
        return when (sub) {
            "tree", "blob", "blame" -> {
                // ref 必须在未解码分段上取：release%2Fv1.2 解码后会被斜杠拆散。
                val ref = restRaw.firstOrNull()?.let(::decodeSegment) ?: return GitHubLinkTarget.Repository(owner, repo)
                GitHubLinkTarget.Files(owner, repo, ref)
            }

            "issues", "pull", "pulls" -> GitHubLinkTarget.Repository(owner, repo)

            "releases" -> parseRelease(owner, repo, rest, restRaw)

            "commit" -> {
                val sha = rest.firstOrNull()
                if (sha.isNullOrBlank()) {
                    GitHubLinkTarget.Commits(owner, repo)
                } else {
                    GitHubLinkTarget.Commits(owner, repo, sha)
                }
            }

            "commits" -> GitHubLinkTarget.Commits(owner, repo, rest.firstOrNull()?.ifBlank { null })

            "actions" -> GitHubLinkTarget.Actions(owner, repo)

            in REPO_TABS -> GitHubLinkTarget.Repository(owner, repo)

            else -> GitHubLinkTarget.External(sourceUrl)
        }
    }

    private fun parseRelease(
        owner: String,
        repo: String,
        rest: List<String>,
        restRaw: List<String>,
    ): GitHubLinkTarget {
        val head = rest.firstOrNull()?.lowercase()
        return when {
            head == "tag" && rest.size >= 2 -> {
                val tag = restRaw.drop(1).map(::decodeSegment).joinToString("/")
                if (tag.isBlank()) GitHubLinkTarget.Releases(owner, repo) else GitHubLinkTarget.Releases(owner, repo, tag)
            }

            head == "latest" -> GitHubLinkTarget.Releases(owner, repo)

            head == "download" && rest.size >= 2 -> {
                val tag = rest[1]
                GitHubLinkTarget.Download(
                    url =
                        "https://github.com/$owner/$repo/releases/download/" +
                            restRaw.drop(1).joinToString("/"),
                    fileName = rest.last().ifBlank { "download.bin" },
                    owner = owner,
                    repo = repo,
                    tag = tag,
                )
            }

            else -> GitHubLinkTarget.Releases(owner, repo)
        }
    }

    private fun parseCodeload(
        segments: List<String>,
        sourceUrl: String,
    ): GitHubLinkTarget {
        if (segments.size < 3) return GitHubLinkTarget.External(sourceUrl)
        val owner = segments[0]
        val repo = segments[1]
        val kind = segments[2].lowercase()
        val ref = segments.drop(3).joinToString("/").ifBlank { "main" }
        val extension =
            when {
                kind.startsWith("tar") -> "tar.gz"
                else -> "zip"
            }
        val suffix = ref.substringAfterLast('/')
        return GitHubLinkTarget.Download(
            url = sourceUrl,
            fileName = "$repo-$suffix.$extension",
            owner = owner,
            repo = repo,
            tag = ref,
        )
    }

    private fun decodeSegment(segment: String): String =
        runCatching { java.net.URLDecoder.decode(segment, "UTF-8") }.getOrDefault(segment)

    private fun normalize(input: String): String =
        input
            .trim()
            .removeSurrounding("<", ">")
            .trim()
}
