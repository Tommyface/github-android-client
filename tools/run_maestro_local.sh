#!/usr/bin/env bash
# 本地真机 E2E：装包 → 跑 Maestro 场景。
#
# 用法：
#   tools/run_maestro_local.sh [APK 路径]
#   GITHUB_PAT=github_pat_xxx tools/run_maestro_local.sh
#
# 约定：
#   - PAT 只从环境变量读，脚本不写盘、不回显；
#   - 无设备直接失败（不许"跳过算通过"）。
set -euo pipefail

APK="${1:-app/build/outputs/apk/debug/app-debug.apk}"
PKG="com.gy.githubclient"

command -v adb >/dev/null || { echo "::error::未找到 adb（需 Android platform-tools）"; exit 1; }
command -v maestro >/dev/null || {
  echo "::error::未找到 maestro，执行：curl -Ls https://get.maestro.mobile.dev | bash"
  exit 1
}

DEVICES=$(adb devices | awk 'NR>1 && $2=="device" {print $1}')
if [ -z "$DEVICES" ]; then
  echo "::error::没有可用设备（adb devices 为空）。设备测试不允许静默跳过。"
  exit 1
fi
echo "设备：$(echo "$DEVICES" | tr '\n' ' ')"

if [ -f "$APK" ]; then
  echo "安装 $APK"
  adb install -r "$APK"
else
  echo "::warning::未找到 $APK，沿用设备上已安装的 $PKG"
fi

if [ -n "${GITHUB_PAT:-}" ]; then
  echo "带凭据运行（PAT 来自环境变量，不落盘）"
  maestro test -e GITHUB_PAT="$GITHUB_PAT" .maestro
else
  echo "无 GITHUB_PAT：跳过 02 登录场景，其余场景要求设备已登录"
  maestro test .maestro --exclude-tags signin 2>/dev/null || maestro test .maestro
fi
