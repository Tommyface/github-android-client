# GitHub Android Client

原生 Android GitHub 客户端。当前定位：**在手机上管理 GitHub**——浏览、编辑、发布、协作与仓库治理，目标是让主要工作流与 GitHub 网页端能力对齐，同时保持原生 Compose 体验。

> 状态：**v0.19.0「写操作地基」第 2 步已落地**（versionCode 22）。
> 已完成：认证 scope 自省与升级入口、能力模型（`core:permission`：scope × 仓库角色）、写操作内核（`core:mutation`：错误分类/幂等重试/确认位）、持久化审计（新模块 `core:audit`，Room `audit.db`），以及首批低风险写操作——Star/Unstar、Watch/Unwatch、Follow/Unfollow、通知线程已读/忽略与订阅开关；权限审计页新增 scope 门控卡与最近操作日志。
> 下一步：文件/分支/Issue 等仓库内容写操作（v0.20）。
>
> 转型方案见 [`v1.0-转型方案-从只读到管理.md`](v1.0-转型方案-从只读到管理.md)。

## 定位与范围

| 层级 | 能力 |
|---|---|
| L1 内容与协作 | 文件增删改、分支、Issue、PR、Review、合并、Release |
| L2 自动化 | Actions 触发/重跑/取消、日志、产物、Secrets/Variables |
| L3 治理 | 仓库设置、可见性、归档/删除、分支保护、协作者、组织只读 |
| L4 长尾 | Projects、Discussions、Wiki、Pages 等，按价值选择性支持 |

明确不做：Billing、Marketplace、Sponsors、Codespaces、企业版管理、App 内云打包与签名、TOTP、LanSync、WebView 壳。

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 + Navigation Compose |
| 架构 | Clean Architecture + MVVM（core + feature × domain/data/presentation） |
| 网络 | Ktor 3.0.3 (OkHttp) · GitHub REST；GraphQL 接入中 |
| DI | Koin 4.0.0 |
| 存储 | Room 2.6.1 + DataStore + Keystore/KSafe |
| 后台 | WorkManager 2.10.0 + Foreground Service |
| 工具链 | AGP 8.7.3 · Gradle 8.11.1 · JDK 20 · compileSdk 35 · minSdk 24 |

## 目录结构

```
app/                      入口 + 导航 + 底部栏
build-logic/convention/   质量门禁的 Gradle 约定插件
core/
  domain/                 接口/模型/用例（纯 Kotlin）
  data/                   Repository/Ktor/Room/下载/镜像
  presentation/           设计系统/通用组件
  permission/             仓库角色 × Token scope 的能力模型（纯 Kotlin）
  mutation/               写操作错误分类/重试策略/审计接口（纯 Kotlin）
  audit/                  Room 持久化审计（audit.db，写操作留痕）
feature/                  auth repo discover release download share search starred profile library
konsist-test/             架构约定测试
config/detekt/ config/spotless/   质量配置
verify.sh                 门禁一键回归
```

## 质量门禁

```bash
./verify.sh
```

| # | 门禁 | 命令 |
|---|---|---|
| 1 | 编译 | `assembleDebug` |
| 2 | 静态分析 | `detekt` |
| 3 | 格式检查 | `spotlessCheck` |
| 4 | 单元测试 | `testDebugUnitTest` |
| 5 | 覆盖率 | `koverVerify` |
| 6 | 架构约定 | `:konsist-test:test` |
| 7 | Android Lint | `lintDebug` |
| 8 | Room 迁移测试 | `*MigrationTest` |

v0.18.0 基线实测：编译、单元测试（155 例）、spotless、detekt 通过；kover/konsist/lint/migration 尚未逐一复跑。v0.19.0 在此基础上新增 scope 自省、写内核、审计与首批写操作的相关测试。

## 本地构建环境

需 JDK 17+ 与 Android SDK (platform 35, build-tools 35.0.0)。在 `local.properties` 配置：

```properties
sdk.dir=/path/to/android-sdk
```

> 实测环境：JDK 20 (Zulu) + Gradle 8.11.1 + Android SDK 35。约定插件中
> Kotlin/Java 的 JVM target 统一为 17；domain 模块 toolchain 为 20（与实测环境一致，
> 若使用 JDK 17 请将 `KotlinLibraryConventionPlugin.kt` 中的 `jvmToolchain(20)` 改回 17）。

## 移植来源

mirror / download / share 部分代码改编自 [komi-store](https://github.com/komi-store/komi-store)（Apache-2.0），已保留来源声明。

## 路线图

- v0.18.0 ✅ 云端 V17 + 链接/翻译/通知/外观/安全融合
- v0.19.0 ✅ 写操作地基：scope 自省、能力模型、写内核、持久化审计、首批低风险写操作（Star/Watch/Follow/通知订阅）
- v0.20.0 仓库内容：文件增删改、分支、图片上传、Markdown 编辑器
- v0.21.0 Issue 全流程
- v0.22.0 PR 与 Review 全流程
- v0.23.0 Release 与 Actions
- v0.24.0 仓库治理与分支保护
- v0.25.0 个人 / 组织 / Gist
- v1.0.0 能力对齐验收（12 条核心旅程）