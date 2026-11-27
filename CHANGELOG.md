# 更新日志

> 本文件遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)；
> 版本号以 `gradle.properties` 的 `ghcVersionName` / `ghcVersionCode` 为唯一真源。

## [Unreleased]

### 变更

- **发版链路（#29）**：`version.json` 的 `fingerprint` 改为从 `apksigner verify --print-certs` 解析的
  **签名证书 SHA-256**。此前该字段误填 APK 文件摘要（与 `spec/ROADMAP.md` M0-08 的证书指纹语义不符），
  导致按证书做校验的客户端永远不可能匹配。解析失败即失败，不允许回退成文件摘要；新增可选
  `GHC_EXPECTED_CERT_SHA256` 钉值断言（未配置时打印 notice，不阻塞密钥轮换）。
- **E2E 资产（#30）**：M4-10 落地 12 条 Maestro 场景（`.maestro/`，覆盖登录页三入口、PAT 登录闭环、
  起始页、底部导航、发现页分类与三榜单口径、我的页 12 入口、通知中心、星标、下载+镜像、应用库、主题切换），
  附本地真机执行手册 `docs/E2E-LOCAL-DEVICE.md` 与 `tools/run_maestro_local.sh`。
- **CI（#27 / #28）**：`nightly` 设备环节改为按 `/dev/kvm` 条件执行；无 KVM 时跳过设备环节并在
  job summary 显式声明，不再每夜假红，也不再假装跑过设备测试。
## [0.19.1] - 2026-11-28

### 变更

- **通知中心（P-209 / PR #23）**：界面文案迁入 `feature/profile/presentation/res/values/strings.xml`（11 条）；
  `NotificationsViewModel` 改为输出 `NotificationsError` 语义类型，不再产出中文字符串；
  `Card` / `MaterialTheme.colorScheme` 全部换为 Frost 令牌；卡片、错误态、空态补齐无障碍语义
  （`contentDescription`、`liveRegion`、装饰元素 `clearAndSetSemantics`）。字符串白名单 37 → 35。
- **发现页（P-210 / PR #25）**：文案迁入 `feature/discover/presentation/res/values/strings.xml`（36 条）；
  `DiscoverViewModel` 输出 `RankNoteKind` / `DiscoverError`，文案下沉界面层；配色全换 Frost 令牌。
  字符串白名单 35 → 33，UI 审计基线同步收紧（`--update-baseline`）。

### 修复

- **发现页历史缺陷**：刷新失败此前完全静默（`ShowMessage` 事件有生产者、消费方丢弃），现改为
  `refreshError` 状态 + 错误行 + 重试；删除卡片内与整卡同义的「详情 / 下载」嵌套 clickable；
  语言（10）/ 分类（6）筛选条在窄屏溢出裁切，改为 `horizontalScroll`；
  `fullName.split("/")` 改用 `ownerLogin` 并在 owner/name 不可解析时不提供点击。
- **重构期回归（CI 捕获）**：`ReleaseDetailScreen` 与 `DiscoverScreen` 复用同一个 `DiscoverViewModel`，
  重构中一度误删 `resolveDownload` / `onDownloadAsset`（编译失败）与既有
  `@Suppress("LongParameterList")`（detekt 阈值 7 触发）。两者均已恢复，并在 KDoc / 注释中标注复用关系。
- **文档漂移**：`spec/ROADMAP.md` 字符串化存量由 39 修正为实测 33；`gradle.properties` 覆盖率实测行
  更新为 `26759/73209 = 36.55%`（CI verify 实测，阈值 33）。

### 无破坏性变更

无权限变更、无新增路由、无 API 行为变更；两屏均为纯重构。

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

### 已发布：v0.19.0（2026-11-27）

- Release：<https://github.com/Tommyface/github-android-client/releases/tag/v0.19.0>
- 产物三件套：`github-android-client-v0.19.0.apk`（3,523,641 字节） + `.sha256` + `version.json`
- APK SHA-256：`6b54686acc1b12bbe763c6b1427e701048d49fac62afe9bfe22d561a1a2c7c62`
- 签名证书 SHA-256：`e86de404352ba7a1a03f3ed80565a6fd56d9f50672fbf106db160d796c62cb77`
  （与 README / SECURITY.md / `ApkSignatureVerifier` 钉的值一致 —— M0-08 由"待固化"变为"已实测确认"）
- 本次发版前修掉了 release 流水线的四处硬伤（此前该流水线**从未实跑过**）：
  1. CHANGELOG 步骤直推受保护的 `main` → 改推一次性分支；
  2. 自动生成器覆盖人工版本说明 → 已有同名章节则跳过生成；
  3. R8 缺失类（`error_prone_annotations` 等 compileOnly 注解）→ 按事实补 `dontwarn`；
  4. release 签名口令环境变量名与约定插件对不上 → 统一为 `GHC_*`；
  5. APK 体积下限按未混淆体积设为 5 MB → 混淆压缩后实测 3.5 MB 被误判 → 下限改 2 MB。
