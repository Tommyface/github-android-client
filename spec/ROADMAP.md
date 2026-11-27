# 执行路线图（依据《GHHub 最优修改报告》）

> 本文件是仓库内的执行主线，与 `docs/versions/`（历史交付记录）、`spec/parity/`（行为规格）配套。
> 状态标记：✅ 已完成 ｜ 🔄 进行中 ｜ ⏳ 未开始。

## 阶段 0 · 地基（3~4 天）— 让项目"可审计"

| 项 | 内容 | 状态 |
|---|---|---|
| M0-01 | 版本单一真源迁 `gradle.properties`，约定插件改读属性 | ✅ |
| M0-02 | CI 三处一致校验（`gradle.properties` == tag == `version.json`） | ✅ |
| M0-03 | `ci.yml`：主路径调 `verify.sh` + 并行预检 + 版本校验 job | ✅ |
| M0-04 | `verify.sh` CI 化：SDK 探测、fast 模式、版本与覆盖率校验 | ✅ |
| M0-05 | `release.yml`：签名构建 + 硬断言 + 产物三件套 + 三处一致校验 | ✅ |
| M0-06 | release 密钥从 Secrets 还原；无密钥直接失败（禁明文兜底） | ✅ |
| M0-07 | release 开启混淆 + 资源压缩 + ProGuard 规则 | ✅ |
| M0-08 | 签名指纹钉子改为钉 release 指纹 | ✅ **已实测确认**：v0.19.0 正式产物的签名证书 SHA-256 = `e86de404…cb77`，与 README / SECURITY.md / `ApkSignatureVerifier` 钉的值一致 |
| M0-09 | kover 阈值门禁（`tools/check_coverage.py`） | 🔄 目标阈值 60%；当前棘轮基线 **33**（`gradle.properties: ghcCoverageThreshold=33`，实测约 34.67%）——距目标仍远，是持续项 |
| M0-10 | Room 结构安全门禁 + schema 导出 + DownloadDatabase v1→v2 迁移 | ✅（真实迁移测试待 schema 首次 bump） |
| M0-11 | konsist 2 → 12 条规则 | ✅ |
| M0-12 | 依赖治理：删 KSafe/jitpack、修正 alias、toolchain 统一 17 | ✅ |
| M0-13 | 清理脚手架残留（PlaceholderScreen / DownloadEntryScreen / 过期 TODO） | ✅ |
| M0-14 | 空壳模块定案：移除零源码模块，`core/presentation` 明确为设计系统归属地 | ✅ |
| M0-15 | 17 份交付报告归档 `docs/versions/` + 版本索引表 | ✅ |
| M0-16 | spec 骨架：PARITY-NOTICE / BASELINE / 模板 / P-101 / ADR-001 / 威胁模型 | ✅ |
| M0-17 | 分支保护配置说明 + PR 模板 + ISSUE 模板 + dependabot + gitleaks 配置 | ✅ |
| M0-18 | 字符串资源化（全量硬编码中文清零） | 🔄 R7 校准后：规则 13 改为**双向棘轮**（新增即失败 / 登记项失效也失败）+ **先剔注释再判定** + 覆盖 feature 层；本轮清掉 5 个文件、修正 7 个误报；剩余 **39** 个文件为已登记存量，逐屏清账 |

## 阶段 1 · D0 发版与更新闭环（4~5 天）— **v0.19.0 已发布（2026-11-27）**

| 项 | 内容 | 状态 |
|---|---|---|
| M1-01 | P-101 UpdateLevel 纯函数 + 15 条验收用例 | ✅ |
| M1-02 | 指纹双信号（统一取 Release 附件 digest / version.json，废弃 `target_commitish`） | ✅（`UpdateCandidateParser` + `UpdateCandidateSourceImpl`） |
| M1-03 | version.json 双通道 + 跳过记指纹 + 30min 安装 TTL | ✅（`UpdateSkipLedgerImpl` + `UpdateCoordinator`） |
| M1-04 | 更新中心 UI（8 状态机可视化 + 跳过按钮） | ✅（`UpdateCheckScreen` 状态胶囊行） |
| M1-05 | 镜像熔断 + 健康度（成功率÷延迟权重） | ✅（`MirrorHealthRegistry` + `MirrorUrlResolver.candidateChain`） |
| M1-06 | CHANGELOG / RELEASE_NOTES 自动生成 | ✅（`tools/generate_changelog.py` + release.yml） |
| M1-07 | 诊断与崩溃体系（分层 Tag + 落盘 + 导出脱敏） | 🔄 脱敏器（幂等）+ 分层日志（按天分文件 / 保留 7 天 / 导出报告）已落地；**崩溃落盘 CrashRecorder 未实现** |
| M1-08 | 安装守卫（包名 / 签名 / versionCode / 未知来源权限） | ✅（纯逻辑 `InstallPreconditionChecker`；Android 适配层随 platform/apkinstall 落地） |
| M1-09 | Link header 分页 | ✅（`LinkHeader` + `GitHubApiClient.requestAllPages`） |
| M1-10 | 网络韧性：指数退避+抖动、Retry-After、二级限流熔断 | ✅（`GitHubApiClient.retryDelayMillis`） |
| M1-11 | `GitHubApiClient` 收敛点补测 | 🔄（LinkHeader/退避已补；错误分支待补） |

## 阶段 2 · D1 高价值写能力（约 1.5 周）

MutationExecutor / 审计日志 / 危险确认 / Issue-PR 写 / Release 创建 / 文件上传 / Actions 日志 /
仓库设置 / 凭据能力矩阵 / 通知中心 / Markwon 可选路径 / 应用库溯源 —— 详见报告 §4.3。

## 阶段 3 · D2 + UI 体系（约 3 周）

GraphQL 开关 / 热力图降级 / PR 合并 / **UI 体系落地（设计系统 + 18 组件）** /
26 屏规格与 Paparazzi / 无障碍与响应式 / 契约测试 / ViewModel 拆分 / ACRA —— 详见报告 §4.4。

## 阶段 4 · D3 长尾（约 2 周）

TOTP / 扫码 / 正文翻译（默认离线）/ 多账号 / 五环降级版 / 收敛内置浏览器 / Lists /
性能基线 / Maestro —— 详见报告 §4.5。

## 第四轮（P-201）落地

- **Frost 设计系统收编**：`core/presentation/theme` 四件套落地，MainActivity 统一走 `GithubClientTheme`；
  konsist 规则 14 锁死"业务屏不得写死色值"。
- **诊断中心**：`platform/diagnostics`（Redactor 脱敏 + StructuredLogger 分层日志已落地；CrashRecorder 崩溃落盘**尚未实现**，见 M1-07）。
- **凭据能力矩阵**：`CredentialKind` 与能力求值器，把"fine-grained 不支持 GraphQL 与通知"变成可展示规则。
- **安装闭环**：`platform/apkinstall` + 下载页「安装」动作 + FileProvider 路径修正。

## 第七轮（P-300）落地 · 尺子校准 + 第一批字符串化

本轮不写新功能，专门修**度量工具本身**：此前三处"尺子"与现实不符，导致门禁既漏检又误报。

| 项 | 问题（实测） | 处置 |
|---|---|---|
| 规则 13 匹配口径 | KDoc 里的中文引号被当成硬编码文案 → 7 个只含注释的文件被登记进白名单 | 判定前先剔注释（新增 `stripComments`），误报清零 |
| 规则 13 扫描范围 | 只扫 app + core/presentation，feature 的 30+ 个界面文件**完全不在门禁内** | 范围扩到全部 feature presentation；存量一次性登记为显式债务 |
| 规则 13 棘轮方向 | 只在"新增即失败"，登记项失效（已还债）不报错 → 白名单只增不减 | 改为**双向棘轮**：登记项失效同样失败，必须同步删行 |
| `tools/ui_audit.sh` | 上限写成 `max 999`，等于没有门禁；nightly 里还带 `\|\| true` | 改为 Python 真棘轮 + `tools/audit_baseline.json` 可机读基线（只减不增），CI 与 nightly 均以 `--strict` 失败退出 |
| 字符串化 | app / core 两模块**零** `strings.xml`，界面文案全硬编码 | 新建 app 与 core/presentation 两份 `strings.xml`；清掉 5 个文件（危险确认 / Frost 图标朗读 / 外壳 Tab / 设置页 / 深链提示） |
| 数字漂移 | 文档与注释写"22 处横向依赖"，实测与清单都是 **23** | 统一为实测值；清单逐条与实测核对一致 |

本轮清掉的界面文件：`DangerConfirmSheet.kt`、`FrostChrome.kt`、`AppScaffold.kt`、`SettingsScreen.kt`、`DeepLinks.kt`。
剩余 39 个文件为**已登记存量**（含 app 的更新流程 3 个文件与 feature 各域），随 UI 里程碑逐屏清账。

## 工具链补充

- `.github/workflows/auto-format.yml`：自动应用 ktlint 格式化并提交，把"格式"从评审话题里拿走。
  注意：由 `github-actions[bot]` 推送触发的后续 CI 可能需要**人工批准**（GitHub 对 bot 推送的
  workflow 运行默认要求确认）；在 Actions 页点「Approve and run workflow」即可，或用个人令牌补一个提交。

## 已知冲突与决策

`feat/frost-ui` 分支已实现 Frost 设计体系、MutationExecutor、审计库、权限能力矩阵、链接解析、
README 翻译与 CI workflow，与阶段 2/3 存在重叠。**合并策略见《GHHub 最优修改报告》§5.1 与 §14.2**：
先完成本分支的阶段 0（CI 与门禁是所有合并的前置），再按 G-对照逐屏验收 frost-ui 的 UI 体系，
通过后以「设计系统归属 core/presentation」的方式收编，避免两套设计体系并存。

> 本轮（M1-02~M1-10）新增门禁与实现，CI 全部由自动格式化流水线兜底格式问题。
