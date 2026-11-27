#!/usr/bin/env bash
# 本地真机 E2E：装包 → 跑 Maestro 场景。
#
# 用法：
#   tools/run_maestro_local.sh [APK 路径]
#   GITHUB_PAT=github_pat_xxx tools/run_maestro_local.sh
#
# 约定：
#   - PAT 只从环境变量读，脚本不写盘、不回显；
#   - 无设备直接失败（不许"跳过算通过"）；
#   - 没有 PAT 时用 `--exclude-tags signin` 跳过 02 登录场景（该场景已声明 tags: [signin]）。
#
# 修掉的两个缺陷（v0.19.2）：
#   1. 旧写法 `--exclude-tags signin` 排除不掉 02——02 当时没有声明任何 tag，
#      于是没 PAT 也会执行它并以"变量未定义"失败；
#   2. 旧写法后面挂了 `|| maestro test .maestro`，把 02 的失败当成"再跑一遍全量"，
#      既掩盖了失败原因，又让 11 条场景重复执行一次。
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
  echo "带凭据运行全部 12 条场景（PAT 来自环境变量，不落盘）"
  exec maestro test -e GITHUB_PAT="$GITHUB_PAT" .maestro
else
  echo "无 GITHUB_PAT：跳过 tags=signin 的 02 登录场景，其余 11 条要求设备已登录"
  # 不加 `|| 重跑`：失败就是失败，要能在报告里看到是哪条场景挂的。
  exec maestro test .maestro --exclude-tags signin
fi