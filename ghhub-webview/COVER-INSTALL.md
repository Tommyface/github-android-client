# GHHub 覆盖安装（Cover Install）说明

覆盖安装 = 安装新 APK 时**覆盖已安装版本并保留应用数据**（token、下载记录、skip 记录等）。
Android 系统对此有强制约束，本工程按方案 S3「ReadyToInstall」把三件事做全：

## 一、覆盖安装的三个硬条件

| 条件 | 系统行为 | 本工程保障 |
|---|---|---|
| 1. 签名一致 | 签名不同 → `INSTALL_FAILED_UPDATE_INCOMPATIBLE`，系统直接拒绝 | 固定签名 `ghhub-release.jks`；`InstallGuard.check()` 预校验签名 SHA-256，不一致提前拦截 |
| 2. versionCode 不降级 | 新包更低 → `INSTALL_FAILED_VERSION_DOWNGRADE` | CI 用 `GITHUB_RUN_NUMBER` 作为 versionCode，单调递增；`InstallGuard` 预校验 |
| 3. 未知来源安装权限（Android 8+） | 未授权 → 安装器一闪而过 | `REQUEST_INSTALL_PACKAGES` 声明 + `InstallGuard.canInstallUnknownApps()` 判定 + 跳转 `ACTION_MANAGE_UNKNOWN_APP_SOURCES` 引导 |

## 二、代码落点

- `update/InstallGuard.kt`：包名 / 签名 SHA-256 / versionCode / 权限的综合前置校验，产出 `CheckResult`（含 `needUnknownAppsPermission` 标志）。
- `webview/WebViewHost.kt::requestInstall`：JS Bridge 主动安装路径，先过守卫，失败回调 `onInstallBlocked`。
- `download/DownloadGateway.kt`：下载成功后不再交给 AppUpdater 直接装（`setInstallApk(false)`），改为 `InstallGuard.check` 后回调 `onInstallReady`。
- `MainActivity.kt::startCoverInstall`：FileProvider 授权 + `ACTION_VIEW` 拉起系统安装器；系统层再做一次签名强校验。
- `ui/MainView.kt::notifyInstallResult`：失败提示 + 无权限时跳授权页。

## 三、构建与出包

```bash
# 本地 debug（用示例密钥签名，可与 CI release 互相覆盖）
export ANDROID_HOME=/path/to/android-sdk
./gradlew :app:assembleDebug

# 本地模拟 CI 递增版本
GHUB_VERSION_CODE=42 GHUB_VERSION_NAME=0.2.0 ./gradlew :app:assembleRelease
```

CI 发布：推送 `v0.2.0` 标签，或 Actions 手动触发 `GHHub Cover-Install Release`。
流水线产出 `GHHub-v0.2.0.apk`、`*.sha256`、`version.json` 并自动创建 Release。

## 四、覆盖安装验证步骤（真机）

1. 先装旧版：`adb install -r app-debug.apk`（versionCode=1）。
2. 装新版（versionCode 更大、同签名）：`adb install -r GHHub-v0.2.0.apk` → 应成功且数据保留。
3. 反向（降级）应被拒绝：`adb install -d` 才可能成功，普通 `-r` 报 `VERSION_DOWNGRADE`。
4. 换签名包应被拒绝：系统报 `UPDATE_INCOMPATIBLE`，`InstallGuard` 也会提前提示「签名不一致」。

## 五、生产环境须知（重要）

仓库内 `app/ghhub-release.jks.b64` 是**示例密钥**（密码 `ghhub2026`），仅用于演示覆盖安装链路。**正式发布必须**：

1. 用 `keytool -genkeypair` 生成你自己的密钥，妥善离线保存；
2. 把 base64 与密码写入 GitHub Secrets：`GHUB_KEYSTORE_B64` / `GHUB_STORE_PASSWORD` / `GHUB_KEY_PASSWORD` / `GHUB_KEY_ALIAS`；
3. 从仓库删除示例 `ghhub-release.jks.b64` 与 workflow 中的明文默认密码；
4. versionCode 一旦发布不可回退，持续递增。

> Keystore 丢失 = 永远无法再覆盖安装已发布版本，务必多地备份。
