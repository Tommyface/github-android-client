# 更新日志

> 本文件遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)；
> 版本号以 `gradle.properties` 的 `ghcVersionName` / `ghcVersionCode` 为唯一真源。

## [Unreleased]

## [0.19.0] - 2026-11-27

### 新增

- **版本单一真源**：`gradle.properties` 统一声明 `ghcVersionName` / `ghcVersionCode` / `ghcCoverageThreshold`，
  构建脚本不再硬编码版本号；CI 校验「gradle.properties == git tag == version.json」三处一致。
- **CI 四件套**：`ci.yml`（8 门禁 + 并行预检 + 版本校验）、`release.yml`（签名构建 + 硬断言 + 产物三件套）、
  `security.yml`（OSV / gitleaks / 依赖锁定 / SBOM）、`nightly.yml`（Maestro / Paparazzi / 基准）。
- **行为规格体系**：`spec/parity/`（基线锁定、清洁室 SOP、七步法、P-XXX 模板与首个规格 P-101）、
  `spec/adr/ADR-001`、`spec/threat-model.md`、`spec/ROADMAP.md`。
- **P-101 UpdateLevel**：纯函数更新语义判定（x.y 变化强制、仅 z 变化可选、同版本指纹不同可选、
  版本不可解析静默、跳过记指纹、30 分钟安装 TTL），附 15 条验收用例。
- **Room 结构安全门禁**：schema 导出 + `RoomSchemaGateTest`（version ≥2 必须有 Migration 且已注册），
  并补齐 DownloadDatabase v1→v2 迁移（新增 `expectedSha256` 列）。
- **覆盖率阈值门禁**：`tools/check_coverage.py`（阈值来自 `gradle.properties`，当前 60%）。
- **协作模板**：PR 模板（规格 ID / 端点探测证据 / UI 三门禁自查）、缺陷与建议 ISSUE 模板、
  dependabot、gitleaks 配置。

### 变更

- release 构建开启**混淆与资源压缩**（新增 `app/proguard-rules.pro`）。
- release 签名改为从 CI Secrets 还原 `app/release.keystore`；**无密钥直接失败，不再有明文口令兜底**。
- 依赖治理：移除未使用的 `KSafe` 与 jitpack 仓库、修正错误的 `fileprovider` alias；
  依赖来源收敛为 `google()` + `mavenCentral()`；Kotlin/Java toolchain 统一为 17。
- 移除零源码空壳模块 `feature/release/*` 与 `feature/share/presentation`（实现时随功能创建）。
- 移除脚手架残留 `PlaceholderScreen` / `DownloadEntryScreen` 与过期 TODO。
- 17 份历史交付报告归档至 `docs/versions/`，并附「版本 → 报告 → tag」索引。

### 安全

- release 签名指纹与 `ApkSignatureVerifier` 的钉子改为指向正式密钥（debug 指纹仅保留兼容期）。
- 威胁模型与写操作安全 Gate 落为 `spec/threat-model.md`，纳入评审流程。

### 已知限制

- `ApkSignatureVerifier` 的正式指纹需在首次用真实密钥发布后固化（当前仍为 debug 指纹，兼容期）。
- 字符串资源化（M0-18）依赖 UI 里程碑的组件化改造，尚未完成。
- Room 的真实 MigrationTestHelper 迁移测试需在 schema 首次 bump 时补齐（历史 schema JSON 尚未入库）。

### 本轮新增（P-205 ~ P-207）

- **P-205 仓库写操作界面**：仓库设置（简介 / 可见性 / 归档）、新建仓库、上传文件三屏与三个 ViewModel；
  删除仓库走 `DangerConfirmSheet` 并要求逐字输入仓库名；上传路径先过 `UploadPathValidator`
  （拒绝绝对路径、`..` 穿越与空段）；新增三条路由 `repo_settings` / `create_repository` / `upload_file`。
- **P-206 Frost 化与无障碍**：「我的」页与「星标仓库」页改用 `FrostCard` 与 Frost 令牌；
  11 个入口按钮、排序筛选片、移除星标按钮补 `contentDescription` / `semantics`。
- **P-207 字符串化第 1 批**：starred 域界面文案全部进 `strings.xml`；
  ViewModel 不再吐中文文案，改为吐语义消息（`StarredMessage` / `StarredErrorKind`）由界面层映射。

### 本轮修掉的门禁缺陷

- 字符串化门禁（konsist 规则 13）此前同时存在**误报**（KDoc 里的中文引号被当成文案）与**漏检**
  （feature 层 30+ 个界面文件根本不在扫描范围内），且只拦"新增"不拦"登记项失效"——
  现改为：先剔除注释再判定、覆盖 feature 全量、**双向棘轮**。
- UI 审计脚本的硬编码中文上限原本写成 `max 999`（形同虚设），nightly 还带 `|| true` 吞掉失败——
  现改为可机读基线的**真棘轮**，CI 与 nightly 均以 `--strict` 失败退出。
- 修掉 P-204 遗留的三处红灯：`RepoDataTest` 缺 `writeApi` 实参、`DangerConfirmSheet` 连续 KDoc、
  领域测试替身未实现新增的四个写操作契约。
