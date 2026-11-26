# 更新日志

> 本文件遵循 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)；
> 版本号以 `gradle.properties` 的 `ghcVersionName` / `ghcVersionCode` 为唯一真源。

## [Unreleased] — v0.19.0 准备中

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
