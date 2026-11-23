# GitHub Android Client

仅安卓端的 GitHub 仓库管理客户端，融合 komi-store 的商店 / 下载 / 镜像加速 / 分享能力。

> 状态：**P3 主线 Feature 进行中（已实测全绿）**（auth 双通道登录 + core 统一网络层已完成 + 8 项门禁 + mirror/download/share 部分骨架）
> 实测：`assembleDebug` BUILD SUCCESSFUL，产出 15MB APK；detekt / spotless / 单测（11 用例）/ kover / konsist / lint 全部通过。
> 交付记录见 `/workspace/P3-auth认证模块交付报告.md`、`/workspace/P3-2-统一网络层交付报告.md`。

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 | Kotlin 2.0.21 |
| UI | Jetpack Compose + Material 3 + Navigation Compose |
| 架构 | Clean Architecture + MVVM（core + feature × domain/data/presentation） |
| 网络 | Ktor 3.0.3 (OkHttp) · GraphQL（待接） |
| DI | Koin 4.0.0 |
| 存储 | Room 2.6.1 + DataStore + Keystore/KSafe |
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
feature/                  auth repo discover release download share search starred profile
konsist-test/             门禁 6：架构约定测试
config/detekt/ config/spotless/   质量配置
verify.sh                 8 项门禁一键回归
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
| 5 | 覆盖率 | `koverVerify` |
| 6 | 架构约定 | `:konsist-test:test` |
| 7 | Android Lint | `lintDebug` |
| 8 | Room 迁移测试 | `*MigrationTest` |

## 本地构建环境

需 JDK 17+ 与 Android SDK (platform 35, build-tools 35.0.0)。在 `local.properties` 配置：

```properties
sdk.dir=/path/to/android-sdk
```

> 本次实测环境：JDK 20 (Zulu) + Gradle 8.11.1 + Android SDK 35。约定插件中
> Kotlin/Java 的 JVM target 统一为 17；domain 模块 toolchain 当前设为 20（与实测环境一致，
> 若你使用 JDK 17 请将 `KotlinLibraryConventionPlugin.kt` 中的 `jvmToolchain(20)` 改回 17）。

## 移植来源

mirror / download / share 部分代码改编自 [komi-store](https://github.com/komi-store/komi-store)（Apache-2.0），已保留来源声明。

## 路线图

- P2 ✅ 脚手架 + 门禁 + 部分移植骨架
- P3 主线 Feature：**auth ✅ 双通道登录 + core 统一网络层 ✅ 已完成** → repo（文件/Issue/PR）→ discover/release
- P4 下载/镜像/分享接入 + 通知双通道（`/notifications` 用 classic PAT）
