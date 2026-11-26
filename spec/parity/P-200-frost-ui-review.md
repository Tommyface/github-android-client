# P-200 · frost-ui 逐屏验收（G-对照）

> 评审对象：分支 `feat/frost-ui`（commit `127c113`），与 `main`（`66bdedb`）对照。
> 评审基准：《GHHub 最优修改报告》§6–§11（UI 体系、信息架构、逐屏规格、状态矩阵、组件清单、
> 交互/动效/无障碍/响应式、文案规范与 UI 交付三门禁）。
> 方法：以 worktree 检出该分支，对 79 个 UI 源文件做**可执行证据采集**（不是印象分）。
> 结论摘要：**组件化与令牌化优于预期，可作为 UI 体系主体收编；但有 4 项硬缺口必须先补。**

## 一、结论

| # | 维度 | 实测证据 | 基准要求 | 判定 |
|---|---|---|---|---|
| 1 | 令牌集中度 | `Color(0x…)` 只出现在 5 个文件，但其中 **3 个是业务屏**（RepoDetailScreen / FileBrowserScreen / CommitScreen） | §6.2「组件不得写死色值，一律取自令牌」 | ❌ **不合规**，需收敛 |
| 2 | 组件复用度 | **53 处** `import com.gy.githubclient.core.presentation.*`（跨 79 个 UI 文件） | §9.2 组件清单（18 个） | ✅ **优于预期**，可直接作为设计系统主体 |
| 3 | 状态覆盖 | 暂无 10 / 空 11 / 失败 24 / 重试 8 / 加载中 3 / 权限 9 / **离线 1** | §9.1 六态矩阵 | ⚠️ **离线态几乎缺失** |
| 4 | 无障碍语义 | `contentDescription` 仅 **1 个**文件；`semantics` **0** | §10.3 对比度/48dp/语义/200% 字号 | ❌ **明显不足** |
| 5 | 字符串化 | **62 个** UI 文件含硬编码中文字面量 | M0-18 棘轮门禁（konsist 规则 13） | ❌ **合并即破门禁**，必须先决策 |
| 6 | 动态取色 | 明确不做，注释写明"避免不同 ROM 下对比度漂移" | §6.3「可选开关，默认关」 | ✅ **取舍兼容**，建议写成 ADR-002 |
| 7 | 对比度 | 令牌注释明确 `textPrimary ≥ 4.5:1`，`primary` 只用于强调与图标 | §6.2 品牌色不得用于正文 | ✅ 基本符合 |

**收编建议**：Frost 体系（`Frost.kt` / `FrostChrome.kt` / `FrostSurface.kt` / `Theme.kt`）
**采纳为 `core/presentation` 的设计系统主体**，但需先补齐下述 4 项缺口。

## 二、可直接采纳的部分

1. **令牌模型**：`FrostColors` 单一 data class 承载全部颜色 + `FrostLevel`（L1 页面 / L2 卡片 / L3 浮层）
   三层玻璃厚度 + `fill(level)` / `edge(level)` 取值函数 —— 与 §6.2「单一色源」完全同构，
   且比"色值 + 语义别名"两层结构更简洁。
2. **Material3 派生**：`Theme.kt` 用 `frostColorScheme()` 从令牌派生 Material3 `ColorScheme`，
   并额外覆盖 `surfaceContainer*` 容器色阶（解决未改造页面出现"白卡片压深底"的硬边问题）——**这一步很专业**。
3. **噪点层**：`LocalFrostNoise` 一次性生成噪点贴图，消除玻璃"塑料感"，且不逐帧重绘。
4. **主题模式**：`ThemeMode.SYSTEM/LIGHT/DARK` + `LocalCodeFontSize`（代码等宽字号可调），
   已覆盖 §6.3 的主题模式与代码字号要求。
5. **组件层**：`FrostChrome.kt`（482 行）/ `FrostSurface.kt`（350 行）承载通用组件，
   53 处复用说明业务屏确实在用它，而不是各写各的。

## 三、必须先补的 4 项缺口

### 缺口 1 · 3 个业务屏写死了色值（违反单一色源）

- 位置：`feature/repo/presentation/.../RepoDetailScreen.kt`、`FileBrowserScreen.kt`、`CommitScreen.kt`
- 风险：改主题时这三屏不会跟随；深色模式可能出现对比度不足。
- 处置：把裸 `Color(0x…)` 换成 `FrostColors` 中的令牌或新增语义别名，并加一条门禁：
  **业务屏出现 `Color(0x` 即失败**（konsist 规则 14，候选）。

### 缺口 2 · 无障碍语义近乎为零

- 实测：`contentDescription` 只在 1 个文件出现，`semantics` 0 处。
- 基准：§10.3 要求"可点击元素必须有 contentDescription 或语义角色"、"列表需 stateDescription"。
- 处置：① 抽 `IconButton` 统一要求描述；② 图标按钮补齐描述；③ 列表项补 `stateDescription`；
  ④ 触控目标 ≥48dp 复核；⑤ 补 Paparazzi 视觉回归后，用截图比对颜色对比度。

### 缺口 3 · 离线态缺失

- 实测：全仓"离线"只出现在 1 处（大概率是文案），没有"断网 → 显示缓存 + 标注缓存时间"的实现。
- 基准：§9.1 六态矩阵把"离线"列为必备态。
- 处置：在 Room 缓存读路径上加 `OfflineBanner`（显示"离线模式，显示 {时间} 的缓存"）。

### 缺口 4 · 62 个文件含硬编码中文 → 合并即破门禁

- 实测：79 个 UI 源文件中 **62 个**含中文字面量。
- 冲突：konsist 规则 13（新增/改名界面文件不得硬编码中文，存量走白名单）会把这些文件全部拦下。
- 处置（二选一，建议 A）：
  - **A（推荐）**：合并时把这批文件加入规则 13 的存量白名单，并把「每屏字符串化」列为 UI 里程碑的
    逐屏任务——棘轮机制保证**只减不增**；同时新增 `strings.xml` 模板与抽取脚本，降低后续成本。
  - **B**：合并前全量字符串化（62 个文件，工作量大、回归风险高，与"先合并再优化"的节奏不符）。

## 四、逐屏对照摘要（G-对照）

| 屏 | frost-ui 状态 | 对照 §8 规格 | 结论 |
|---|---|---|---|
| 登录 LoginScreen | 已重做 | §8-01 登录 | ✅ 覆盖 |
| 权限体检 PermissionAuditScreen | 已重做 | §8-02 权限体检 | ✅ 覆盖 |
| 仓库列表 RepoListScreen | 已重做 | §8-03 仓库列表 | ✅ 覆盖 |
| 仓库详情 RepoDetailScreen | 已重做（466 行改动） | §8-04 仓库详情（分段） | ⚠️ 是否真的"分段"需复核；且有裸色值 |
| 文件浏览 FileBrowserScreen | 已改 | §8-05 文件浏览 | ✅ 覆盖 |
| 提交 CommitScreen | 已改 | §8-07 提交历史 | ⚠️ 有裸色值 |
| 搜索 SearchScreen | 已重做（397 行改动） | §8 搜索 | ✅ 覆盖 |
| 发现 DiscoverScreen / ReleaseDetail / ReleaseList | 已重做 | §8 发现/Release | ✅ 覆盖 |
| 下载 DownloadScreen / MirrorSettings | 已重做 | §8-19/20 | ✅ 覆盖 |
| 个人中心 ProfileScreen 等 | 已改 | §8-23 个人中心 | ✅ 覆盖 |
| 设置 SettingsScreen（新增） | 新增 | §8 外观/镜像/账号 | ✅ 补位 |
| 深链 DeepLinks（新增） | 新增 | §7 路由 | ✅ 补位（我原计划里 M4 才有） |
| 贡献热力图 | 仍缺失 | §8-22 | ⏳ 依赖 GraphQL（M3-01） |

**"半成品界面无入口"检查（教训 #3）**：frost-ui 新增了 `SettingsScreen` 与 `DeepLinks`，
需逐一确认它们**在导航里有可达入口**——这正是 G-对照门禁要机器化检查的部分，
建议新增一条**路由↔入口双向差集**脚本（见《最优修改报告》§11.2）。

## 五、收编后的合并动作清单

1. 把 frost-ui 的 62 个 UI 文件加入 konsist 规则 13 存量白名单（并同步更新数量）。
2. 收敛 3 个业务屏的裸色值；新增 konsist 规则 14「业务屏不得出现 `Color(0x`」。
3. 补无障碍语义：图标按钮描述、`stateDescription`、48dp 复核。
4. 补离线态（`OfflineBanner`）。
5. 写 `spec/adr/ADR-002-不做动态取色.md` 记录取舍理由。
6. 新增"路由↔入口"差集脚本并纳入 CI（G-对照机器化）。
7. 合并后按 §8 逐屏补齐剩余 12 屏（更新中心 8 态胶囊、TOTP、通知中心、Actions 日志、诊断导出等）。

## 六、评审方法学（可复用于后续 UI 评审）

评审不靠"看起来不错"，而是**用可执行脚本取证据**：

```bash
# 令牌集中度：业务屏里出现裸色值即不合规
grep -rln "Color(0x" --include="*.kt" feature/*/presentation/src

# 组件复用度：跨文件 import core.presentation 的处数
grep -rn "import com.gy.githubclient.core.presentation" --include="*.kt" feature/*/presentation/src | wc -l

# 状态覆盖：六态关键词命中文件数
for k in 暂无 失败 重试 加载中 离线 权限; do grep -rl "$k" --include="*.kt" feature/*/presentation/src | wc -l; done

# 无障碍语义覆盖
grep -rl "contentDescription" --include="*.kt" feature/*/presentation/src | wc -l
grep -rl "semantics" --include="*.kt" feature/*/presentation/src | wc -l
```

这套命令应固化为 `tools/ui_audit.sh`，让「UI 是否达标」变成**可重复执行的审计**，
而不是每次评审都靠人肉看图。
