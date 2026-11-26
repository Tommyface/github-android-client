#!/bin/bash
# 8 项质量门禁一键回归（门禁唯一真源，CI 与本地共用同一脚本）
#
# 用法：
#   ./verify.sh          # 全量 8 项
#   ./verify.sh --fast   # 跳过最慢的 lint 与迁移测试（本地快速迭代）
#
# 环境要求：JDK 17+、Android SDK（通过 ANDROID_HOME / ANDROID_SDK_ROOT 或 local.properties 指定）。
# CI 不允许复制本文件里的门禁逻辑——两套规则必然漂移。
set -euo pipefail
cd "$(dirname "$0")"

FAST=0
if [ "${1:-}" = "--fast" ]; then
  FAST=1
fi

# ── 环境探测：CI 与本地都不得依赖硬编码路径 ───────────────────────
if [ -z "${ANDROID_HOME:-}" ] && [ -z "${ANDROID_SDK_ROOT:-}" ]; then
  if [ -f local.properties ] && grep -q '^sdk.dir=' local.properties; then
    SDK_DIR=$(grep '^sdk.dir=' local.properties | cut -d= -f2 | sed 's/\\:/:/g')
    export ANDROID_HOME="$SDK_DIR"
    export ANDROID_SDK_ROOT="$SDK_DIR"
  elif [ -d "/opt/android-sdk" ]; then
    export ANDROID_HOME="/opt/android-sdk"
    export ANDROID_SDK_ROOT="/opt/android-sdk"
  else
    echo "::error::未找到 Android SDK。请设置 ANDROID_HOME 或在 local.properties 写 sdk.dir=..."
    exit 1
  fi
fi
export ANDROID_HOME="${ANDROID_HOME:-$ANDROID_SDK_ROOT}"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-$ANDROID_HOME}"

echo "========== GitHub Android Client · 8 项质量门禁 =========="
echo "JDK: $(java -version 2>&1 | head -1)"
echo "SDK: $ANDROID_HOME"
echo "模式: $([ "$FAST" -eq 1 ] && echo 'fast（跳过 lint 与迁移测试）' || echo 'full')"
echo ""

echo "[0/8] 版本单一真源校验（gradle.properties == git tag） ..."
if [ -f tools/verify_version.py ]; then
  python3 tools/verify_version.py
fi

echo "[1/8] 编译 assembleDebug ..."
./gradlew assembleDebug --no-daemon

echo "[2/8] 静态分析 detekt ..."
./gradlew detekt --no-daemon

echo "[3/8] 格式检查 spotlessCheck ..."
./gradlew spotlessCheck --no-daemon

echo "[4/8] 单元测试 testDebugUnitTest ..."
./gradlew testDebugUnitTest --no-daemon

echo "[5/8] 覆盖率 koverVerify + 阈值门禁 ..."
./gradlew koverVerify koverXmlReport --no-daemon
python3 tools/check_coverage.py

echo "[6/8] 架构约定 konsistTest ..."
./gradlew :konsist-test:test --no-daemon

if [ "$FAST" -eq 1 ]; then
  echo "[7/8] Android Lint ... (fast 模式跳过)"
  echo "[8/8] Room 迁移测试 ... (fast 模式跳过)"
else
  echo "[7/8] Android Lint ..."
  ./gradlew lintDebug --no-daemon

  echo "[8/8] Room 迁移测试 ..."
  # 迁移测试源码存在时才执行，避免 --tests filter 无匹配导致任务假性 FAILED。
  if grep -rq "MigrationTest" --include="*.kt" app/src core feature 2>/dev/null; then
    ./gradlew :app:testDebugUnitTest --tests "*MigrationTest" --no-daemon
  else
    echo "  (暂无 migration test，跳过)"
  fi
fi

echo ""
echo "========== 全部门禁通过 =========="
APK=app/build/outputs/apk/debug/app-debug.apk
if [ -f "$APK" ]; then
  echo "APK: $APK"
  md5sum "$APK"
  sha256sum "$APK"
fi
