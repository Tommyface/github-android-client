# GitHub Android Client

仅安卓端的 GitHub 仓库管理客户端，融合 komi-store 的商店 / 下载 / 镜像加速 / 分享能力。

> 状态：**v0.19.0 准备中**（当前主干 v0.17.0 / versionCode 20；下一版跳过被占用的 v0.18.0）
> 交付历史见 `docs/versions/`（版本 → 报告 → tag 对应表）。
> 对标与合规边界见 `spec/parity/`，威胁模型见 `spec/threat-model.md`，执行路线见 `spec/ROADMAP.md`。

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 + Navigation Compose |
| 架构 | Clean Architecture + MVVM（core + feature × domain/data/presentation） |
| 网络 | Ktor 3.0.3 (OkHttp) · GraphQL（待接） |
| DI | Koin 4.0.0 |
| 存储 | Room 2.6.1（schema 导出 + 迁移门禁）+ DataStore + Keystore |
| 后台 | WorkManager 2.10.0 + Foreground Service |
| 工具链 | AGP 8.7.3 · Gradle 8.11.1 · compileSdk 35 · minSdk 24 |

## 目录结构

```
app/                      入口 + 导航 + 底部栏骨架
build-logic/convention/   8 项门禁的 Gradle 约定插件
core/
  domain/                 接口/模型/用例（纯 Kotlin）+ 已移植的 mirror/share/download 领域层
  data/                   Repository/Ktor/Room + 已移植的 MirrorRewriter/PartialDownload/AndroidShareManager
  presentation/           设计系统/通用组件
feature/                  auth repo discover download search starred profile library（每域三层）
platform/                 系统能力层（apkinstall / diagnostics / guard / totp / backup）
spec/                     行为规格（parity）/ 威胁模型 / ADR
tools/                    版本校验 / 覆盖率阈值 / 指纹 / SBOM
konsist-test/             门禁 6：架构约定测试（12 条规则）
config/detekt/ config/spotless/   质量配置
.github/workflows/        ci / release / security / nightly
verify.sh                 8 项门禁一键回归（CI 与本地共用同一真源）
```

## 8 项质量门禁

```bash
./verify.sh
```

| # | 门禁 | 命令 |
|---|---|---|
| 1 | 编译 | `assembleDebug` |
| 2 | 静态分析 | `detekt` |
| 3 | 格式检查 | `spotlessCheck` |
| 4 | 单元测试 | `testDebugUnitTest` |
| 5 | 覆盖率 | `koverVerify` + `koverXmlReport` + **阈值门禁**（`tools/check_coverage.py`，阈值见 `gradle.properties`） |
| 6 | 架构约定 | `:konsist-test:test`（**12 条** konsist 规则） |
| 7 | Android Lint | `lintDebug` |
| 8 | Room 结构安全 | `RoomSchemaGateTest`（version ≥2 必须有 Migration 并注册） |

> 另有 4 项机器门禁由 CI 承担：依赖漏洞扫描（OSV）、密钥泄漏扫描（gitleaks）、
> 依赖版本锁定校验、制品签名校验；以及 3 条流程门禁：真机五步冒烟、规格逐屏对照、Release 成熟度声明。

### 版本单一真源

版本号只在 `gradle.properties` 定义一次：

```properties
ghcVersionName=0.19.0
ghcVersionCode=21
ghcCoverageThreshold=60
```

CI 会校验 `gradle.properties == git tag == version.json` 三处一致（`tools/verify_version.py`）。

## 本地构建环境

需 JDK 17+ 与 Android SDK (platform 35, build-tools 35.0.0)。在 `local.properties` 配置：

```properties
sdk.dir=/path/to/android-sdk
```

> 实测环境：JDK 17 (Zulu) + Gradle 8.11.1 + Android SDK 35。约定插件中
> Kotlin/Java 的 JVM target 与 domain 模块 toolchain 已统一为 **17**。

### 发布签名

- `debug` 使用入库的 `app/debug.keystore`（公开口令，仅供内部覆盖安装调试）。
- `release` 使用 CI 从 Secrets 还原的 `app/release.keystore`；
  **无密钥时 release.yml 直接失败，不允许明文口令兜底**（避免历史事故：示例密钥入库 + 口令明文）。

## 移植来源

mirror / download / share 部分代码改编自 [komi-store](https://github.com/komi-store/komi-store)（Apache-2.0），已保留来源声明。

## 分支与发布

- `main`：受保护，禁止直推；PR + ≥1 review + CI 全绿 + squash 合并。
- `feature/P-XXX-slug`：功能分支，ID 取自 `spec/parity/` 的行为规格编号。
- `hotfix/P-XXX-slug`：从上一个 tag 切出，回合 main 后立即打 patch tag。
- tag **由 CI 创建**，禁止本地手打；版本语义焊死在 `gradle.properties`。

详见 `spec/ROADMAP.md`。
