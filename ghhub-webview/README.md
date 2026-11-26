# GH Hub — Android 工程

面向中国网络环境的 GitHub 应用更新助手。依据《优化后项目方案 —— 差异化定位 + 成熟组件复用》与 UI 设计稿实现，严格守住「只做三件自研、其余复用」的边界。

## 已验证（2026-11-26 本机实测）

| 验证项 | 结果 |
|---|---|
| `:app:compileDebugKotlin` | ✅ BUILD SUCCESSFUL（0 error） |
| `:app:assembleDebug` | ✅ 生成 `app-debug.apk`（7,589,180 B） |
| `:app:testDebugUnitTest` | ✅ 10 tests / 0 failures（状态机三条硬约束全绿） |
| APK 包信息 | `com.ghhub.app` v0.1.0 / minSdk 24 / targetSdk 34 |

APK 已复制到工作区：`/workspace/GHHub-调试版.apk`（MD5 `15aa0a6da71413212cc906f250ab572a`）。

### 覆盖安装（2026-11-26 新增并实测）

| 验证项 | 结果 |
|---|---|
| 固定签名生效 | ✅ debug 与 release 证书 SHA-256 完全一致（`98abe593…e26f2`），可互相覆盖 |
| versionCode 递增 | ✅ CI 注入 42 → release 包 `versionCode=42 / versionName=0.2.0` |
| 覆盖安装守卫 | ✅ `InstallGuard` 校验签名 SHA-256 / 非降级 / Android 8+ 未知来源权限 |
| 签名 release 包 | `/workspace/GHHub-v0.2.0-覆盖安装版.apk`（6,149,417 B，MD5 `02262f16…4811`） |

覆盖安装原理、真机验证步骤、生产密钥替换须知见 [`COVER-INSTALL.md`](COVER-INSTALL.md)。
CI 流水线：`.github/workflows/ghhub-cover-install-release.yml`（推 `v*` 标签或手动触发，固定签名 + 递增版本 + 自动 Release）。

## 构建方式

本机需 Android SDK（platform 34 + build-tools 34.0.0）与 JDK 17+：

```bash
# 1. 配置 SDK 路径（二选一）
echo "sdk.dir=/你的/Android/Sdk" > local.properties
# 或 export ANDROID_HOME=/你的/Android/Sdk

# 2. 构建 / 测试
./gradlew :app:assembleDebug       # 出包
./gradlew :app:testDebugUnitTest   # 状态机单测
```

> 首次构建需联网下载 Gradle 8.7、AGP 8.5.2 与依赖（google() / mavenCentral()）。

## 架构与方案对应

| 方案章节 | 落地文件 | 说明 |
|---|---|---|
| 五·复用策略 | `app/build.gradle.kts` | 依赖 `com.github.jenly1314.AppUpdater:app-updater:2.2.0`（实际 GAV，注意包名 `com.king.app.updater`）、OkHttp、security-crypto |
| 6.1 架构 | `MainActivity.kt` | 单 Activity + WebView 壳 + 四 Tab；装配状态机/镜像/下载桥 |
| 6.1 桥 | `webview/JsBridge.kt` | 裁剪版桥：鉴权/网络/下载/更新/诊断五类（对照 githup 116 方法裁剪） |
| 6.1 WebView | `webview/WebViewHost.kt` | github.com 网页端 + DownloadListener 捕获 + 多标签 + 外链打开 |
| 6.2 状态机 | `core/UpdateState.kt`、`core/UpdateDecider.kt` | 8 状态 sealed class；NoUpdate 静默、Unknown 绝不兜底 0.0.0、指纹不一致强更、skip 双键 |
| 6.2 双通道 | `update/UpdateChecker.kt` | Release API + version.json 并行，谁先成功用谁 |
| 6.2 节流 | `core/UpdatePreferences.kt` | checks_rate_limit_secs + 检查日期持久化 |
| 6.3 镜像 | `mirror/MirrorConfig.kt`、`MirrorLatencyProbe.kt`、`MirrorSelector.kt` | 测速选源、失败自动切源、成败计入权重、自定义源（#35） |
| 下载（#39/#41） | `download/DownloadGateway.kt`、`log/StructuredLogger.kt` | AppUpdater 承载下载/MD5/安装；结构化 JSONL 日志一键导出 |
| Token | `security/SecureTokenStore.kt` | EncryptedSharedPreferences + AES256 |
| S4 多应用 | `apps/ManagedApp.kt`、`AppRegistry.kt` | 记录「从本应用下载过的 GitHub 应用」，四行状态语义 |

## 已知限制（诚实说明）

1. **UI 为代码构建的临床级原型**：使用线性布局 + 设计令牌色值，视觉接近设计稿但未做像素级还原与深色/浅色动态切换（设计稿双主题在 HTML 设计稿与 Web 原型中完整呈现）。
2. **双通道指纹来源**：GitHub Release 通道用 `target_commitish` 前 8 位作为指纹示意；生产环境应接入 APK 的 sha256 清单并与发布流水线对齐。
3. **version.json 为可选通道**：被测仓库若未提供该文件，仅 Release 通道生效。
4. **未做签名发布包**：仅 `assembleDebug`（调试签名），正式发布需配置 signingConfigs + 一键发布脚本（方案 S5）。
5. **权限提示**：Android 13+ 首次启动会请求通知权限（用于下载进度），拒绝后仍可下载，仅通知栏无进度。
