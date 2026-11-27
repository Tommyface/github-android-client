# 本地真机端到端（Maestro）

> 定位：M4-10 的场景资产 + 本机执行手册。
> **CI 不跑设备测试**：GitHub hosted runner 无 `/dev/kvm`，emulator 37.x 拒绝软件 CPU 模拟
> （`nightly` 的「设备环节不可用声明」步骤每次都会写明这一点）。设备验证走本地真机。

## 一、前置

```bash
# 1. 设备可见（USB 调试已开，或无线调试）
adb devices

# 2. 安装 Maestro（一次性）
curl -Ls "https://get.maestro.mobile.dev" | bash
export PATH="$PATH:$HOME/.maestro/bin"

# 3. 装包（debug 或正式版均可，包名一致 com.gy.githubclient）
adb install -r app/build/outputs/apk/debug/app-debug.apk
# 正式产物：github-android-client-v0.19.1.apk
```

## 二、跑法

```bash
# 全套（12 条）。02 会在场景内自己登录（clearState 冷启），其余 11 条要求设备已登录。
maestro test .maestro

# 只跑登录闭环：PAT 从环境变量注入，不进仓库
maestro test -e GITHUB_PAT=github_pat_xxx .maestro/02-sign-in-pat.yaml

# 没有 PAT 时：跳过 02（它带 tags: [signin]），其余 11 条照跑
maestro test .maestro --exclude-tags signin

# 一键包装脚本（装包 + 跑全量；有 GITHUB_PAT 就带上，没有就自动排除 02）
tools/run_maestro_local.sh app/build/outputs/apk/debug/app-debug.apk
```

首次建议顺序：`01` → `02`（登录）→ 其余场景。

## 三、场景清单（12 条）

| 文件 | 覆盖 | 是否需登录 |
|---|---|---|
| `01-login-page` | Device Flow / PAT 主通道 / 通知通道绑定三入口齐备 | 否（`clearState`） |
| `02-sign-in-pat` | PAT 登录闭环 → 落到起始页 | 否（场景内登录） |
| `03-home-repo-list` | 起始页 `Route.Home` 与底部导航 | 是 |
| `04-bottom-nav` | 仓库/发现/搜索/我的 四 tab 往返 | 是 |
| `05-discover-categories` | 发现页分类 chips 切换 | 是 |
| `06-discover-rank-tabs` | 趋势/新发布/热门 三口径说明 | 是 |
| `07-profile-entries` | 我的页 12 个功能入口 + 退出登录 | 是 |
| `08-notifications` | 通知中心（未读计数用正则）+ 返回 | 是 |
| `09-starred` | 星标页排序 chips | 是 |
| `10-downloads-mirror` | 下载管理 → 镜像加速设置 | 是 |
| `11-library` | 应用库入口与批量更新 | 是 |
| `12-settings-theme` | 主题切换 + 说明文案 | 是 |

「登录后」类场景假设设备上已有有效凭据；`clearState: true` 会把登录态一起清掉，非登录场景不要加。

## 四、凭据约定

- PAT **只从 `-e GITHUB_PAT=` 注入**，任何场景文件里不得出现真实 token；
- 场景文件里出现 `${GITHUB_PAT}` 是变量引用，不是泄漏；
- 若需要在报告中留证据，只贴场景名与断言结果，不要贴 token。

## 五、维护约定

- 断言文案必须与 `strings.xml` / 界面实现**逐字一致**——每条断言对应哪一行代码，见附录 A；
- 文案改动（P-209/P-210 这类字符串化重构）后必须同步更新对应场景，否则场景会假红；
- Maestro 的 `text` 选择器是**全匹配正则**：`.*` 才是"包含"，`.` 是通配符；
  `assertVisible: "通知中心（未读 [0-9]+）"` 之所以能用，是因为它恰好匹配整条文本；
- 带动态值的断言用正则（如未读计数）；
- Emoji 前缀（`⭐` / `🔔` / `🎨`）带变体选择符，断言里不要逐字锚定，用 `.*关键词.*`；
- 条件渲染的文案不能拿来断言（例：`清空已完成记录` 只在有已完成/失败任务时出现）；
- 跨屏入口一律 `scrollUntilVisible` + `assertVisible`，不要假定"点得到"；
- **可排除的场景必须声明 `tags`**，否则 `--exclude-tags` 形同虚设（02 的 `signin` 就是为此加的）。

## 六、首跑状态（必须如实标注）

本批场景**尚未在真机上执行过一次**——沙箱无设备、CI 无 KVM。
首次真机跑的失败项请回到本文件更新状态，并区分两类原因：

1. **场景写错**（选择器/文案不准）→ 改场景；
2. **应用真缺陷**→ 提 issue / 修代码。

不要为了让场景变绿而放宽断言。

### 静态复核已发现的缺陷（v0.19.2 已修，先于首跑）

场景从未跑过，只能做静态审阅。逐条把断言对回代码时，查出下面这些问题——它们都会在首跑时变红，
但原因各不相同，务必分清：

| # | 问题 | 性质 | 处置 |
|---|---|---|---|
| 1 | `01` 里 `github_pat_...` 断言写了两遍，通知通道的 `ghp_...` 一次没断 | 场景写错（复制粘贴） | 改断言，并补上通知卡片标题 |
| 2 | `02` 未声明 `tags`，`--exclude-tags signin` 排除不掉它 | 场景写错 | 加 `tags: [signin]` |
| 3 | 脚本 `--exclude-tags signin \|\| maestro test .maestro`：排除不掉 → 必然失败 → `\|\|` 再全量重跑一遍，把失败掩盖成"跑了两遍" | 脚本缺陷 | 去掉 `\|\|`，失败即失败 |
| 4 | `10` 断言 `清空已完成记录`，该文案只在 `stats.done + stats.failed > 0` 时渲染 | 场景写错（拿条件渲染做断言） | 改断 `下载管理` / `镜像设置` |
| 5 | `07`/`08`/`10` 直接 `assertVisible`/`tapOn` 第 6–12 项入口，而这些入口在"我的"页第二屏 | 场景写错（未滚动） | 全部改 `scrollUntilVisible` |
| 6 | `01` 三张卡片在多数机型超过一屏，而 `LoginScreen` 的根 `Column` 没有滚动容器 | **应用真缺陷**（小屏上「绑定通知通道」不可达） | 给 `LoginScreen` 加 `verticalScroll` |
| 7 | `09` 的 chips 同时挂了 `label` 与 `contentDescription`，无障碍树可能只暴露后者 | 断言过窄 | 改 `.*最近更新.*` 覆盖两种暴露方式 |
| 8 | `03` 用 `"仓库"` 判"在仓库页"，而这个词在底部 tab 上也有 | 断言无区分力 | 补 `GitHub · 磨砂玻璃`（仅通栏渲染） |

第 6 条是本轮唯一改到**应用代码**的一处：`feature/auth/.../LoginScreen.kt` 的根 `Column` 补了
`.verticalScroll(rememberScrollState())`（高度仍由 `fillMaxSize()` 定，内容不足一屏时 `Arrangement.Center` 照旧生效）。

## 附录 A、断言来源（逐条可追溯）

每一行都是"从代码里读出来的"，不是从截图或记忆里编的。改文案时按这张表反查就不会漏改场景。

| 场景 | 断言 / 动作 | 来源 |
|---|---|---|
| 01 | `使用 GitHub 账号登录` | `LoginScreen.kt` · `OAuthDeviceFlowCard` Idle 态 `Button` |
| 01 | `主通道 · Fine-grained Token（备用）` | `LoginScreen.kt` · PAT 卡片 `Text` |
| 01 | `github_pat_...` | `LoginScreen.kt` · PAT `OutlinedTextField(label=…)` |
| 01 | `通知通道 · Classic Token（可选）` | `LoginScreen.kt` · 通知卡片 `Text` |
| 01 | `ghp_...` | `LoginScreen.kt` · 通知 `OutlinedTextField(label=…)` |
| 01 | `绑定通知通道` | `LoginScreen.kt` · 通知卡片 `TextButton` |
| 02 | `Fine-grained Token 输入框` | `LoginScreen.kt` · PAT 输入框 `semantics.contentDescription` |
| 02 | `登录` | `LoginScreen.kt` · 提交 `Button`（非 `isSubmitting`） |
| 02 | `我的仓库` | `RepoListScreen.kt` · 顶部标题 |
| 03 | `我的仓库` / `搜索仓库` | `RepoListScreen.kt` · 标题、搜索框 label |
| 03 | `GitHub · 磨砂玻璃` | `app/res/values/strings.xml` · `nav_tab_repo_subtitle`（`AppScaffold` 通栏渲染） |
| 04 | `发现` / `搜索` / `我的` / `仓库` | `app/res/values/strings.xml` · `nav_tab_discover` / `nav_tab_search` / `nav_tab_profile` / `nav_tab_repo` |
| 04 | `热门仓库 · 最新 Release` | `nav_tab_discover_subtitle` |
| 04 | `仓库 · 代码 · 用户` | `nav_tab_search_subtitle` |
| 04 | `功能` | `ProfileScreen.kt` · 功能区分组标题 |
| 05 | `发现 · 商店` / `精选开源项目 · Release 实时更新 · 支持镜像加速下载` | `discover/presentation/res/values/strings.xml` · `title` / `subtitle` |
| 05 | `开发` / `影音` / `全部` | 同上 · `category_developer` / `category_media` / `category_all` |
| 06 | `趋势` / `新发布` / `热门` | 同上 · `tab_trending` / `tab_new` / `tab_hot` |
| 06 | `非官方近似 · 近 30 天新建按 ★ 排序 · 基于 Search API` | 同上 · `rank_note_trending`（`RankNoteKind.TRENDING`） |
| 06 | `非官方榜单 · 按最近更新排序 · 基于 Search API` | 同上 · `rank_note_new` |
| 06 | `非官方榜单 · 按 ★ 排序 · 基于 Search API` | 同上 · `rank_note_hot` |
| 07 | 12 个入口文案（⭐ 星标仓库 / 🕒 个人动态 / 📝 我的 Gist / 🏢 组织信息 / 📱 应用库 / 🔔 通知中心 / 下载管理 / 镜像加速设置 / 🎨 外观与缓存 / 🔐 权限体检 / ⬆️ 检查更新 / 📊 贡献热力图（网页查看）） | `ProfileScreen.kt` · 12 个 `FullWidthButton(text=…)` |
| 07 | `退出登录` | `ProfileScreen.kt` · 底部 `OutlinedButton` |
| 08 | `通知中心（未读 [0-9]+）` | `profile/presentation/res/values/strings.xml` · `title` = `通知中心（未读 %1$d）` |
| 09 | `最近更新` / `最近创建` | `starred/presentation/res/values/strings.xml` · `sort_updated` / `sort_created`（`FilterChip.label`） |
| 09 | （chip 的另一路暴露） | `StarredScreen.kt` · `FilterChip` 的 `contentDescription` = `sort_*_desc` |
| 10 | `下载管理` / `镜像设置` | `DownloadScreen.kt` · 标题、`TextButton` |
| 10 | `镜像加速设置` / `镜像 URL 模板` | `MirrorSettingsScreen.kt` · 标题、`label` |
| 11 | `应用库` / `全部更新` | `LibraryScreen.kt` · 标题、恒渲染的 `OutlinedButton` |
| 12 | `主题` | `app/res/values/strings.xml` · `settings_section_theme` |
| 12 | `深色` | 同上 · `settings_theme_dark`（经 `themeModeLabel(ThemeMode.DARK)`） |
| 12 | `深色模式同样适用于 README、代码块与 Diff。` | 同上 · `settings_theme_hint` |
