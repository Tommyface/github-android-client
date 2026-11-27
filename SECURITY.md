# 安全策略

## 一、报告漏洞

请通过 GitHub Security Advisory（Private vulnerability reporting）私下报告，**不要**开公开 Issue。

报告内容建议包含：设备与系统、App 版本、复现步骤、脱敏后的诊断报告
（设置 → 诊断与导出 → 导出报告，导出前已自动脱敏）。

我们承诺在收到报告后 48 小时内确认，并在修复版本发布时同步公告。

## 二、凭据处理

- Token 仅存于 AndroidKeyStore 加密的 `EncryptedSharedPreferences`；不落 `localStorage`、不进日志、不进领域模型。
- 「清除全部本地数据」会一并吊销本地凭据（设置 → 账号与安全）。
- 诊断导出内容已做脱敏：令牌、Authorization 头、私有仓库路径不会出现在导出文件中。

## 三、供应链

- 正式产物经 **SHA-256（官方 digest）+ 签名证书指纹** 双重校验；不一致即删除文件并终止安装。
- 下载镜像仅用于**产物**下载；API 与凭据流量不走第三方镜像。
- 依赖仅来自 `google()` 与 `mavenCentral()`；CI 每周执行漏洞扫描（OSV）与密钥泄漏扫描（gitleaks）。

## 四、发布签名

- 正式包使用 CI Secrets 还原的 release 密钥；**仓库内不存在任何正式密钥**。
- `app/debug.keystore` 是刻意入库的公开调试密钥（口令 `android`），仅供内部覆盖安装调试，
  **不得**用于正式分发。
- 正式签名证书 SHA-256 指纹：

  ```
  e86de404352ba7a1a03f3ed80565a6fd56d9f50672fbf106db160d796c62cb77
  ```

  请以本文件/仓库公布的指纹为准校验下载包。

  离线复核（只需 Python 3，不需要 Android SDK / apksigner）：

  ```bash
  python3 tools/verify_apk_cert.py github-android-client-v0.19.1.apk \
      --expect e86de404352ba7a1a03f3ed80565a6fd56d9f50672fbf106db160d796c62cb77
  ```

  脚本直接从 APK 签名块（v2 / v3）读证书并算 SHA-256，与 `apksigner verify --print-certs`
  是两条独立路径，发版流程要求两者一致。注意它**只取证书指纹，不验证签名有效性**——
  签名是否可校验仍以 `apksigner verify` 为准。

**密钥保管须知（务必执行）**

1. release keystore 与口令**必须离线备份**（至少两处，含一份离线介质）。
2. keystore 丢失或口令遗忘 = **永久无法发布可覆盖安装的新版本**（用户必须卸载重装，数据丢失）。
3. 备份时请同时记录口令；不要把 keystore 与口令放在同一位置。
4. 密钥已在 GitHub Actions Secrets 中以 sealed-box 加密形式托管（`GHC_KEYSTORE_B64` 等四项），
   轮换密钥需同时更新 Secrets、`ApkSignatureVerifier.RELEASE_CERT_SHA256` 与本文件。

## 五、权限

目标 ≤6 项，且按需申请（关闭相关功能后权限可被系统回收）。
新增权限须在 PR 中说明理由，并由 `RoomSchemaGateTest`/konsist 规则 9 复核。

## 六、威胁模型

见 `spec/threat-model.md`。
