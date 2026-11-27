#!/usr/bin/env bash
# UI 审计入口（P-200 方法学固化 → R7 起为**真棘轮**）。
#
# 判定逻辑全部在 tools/ui_audit.py（Python），本文件只做入口转发，
# 保持 `bash tools/ui_audit.sh [--strict]` 的既有调用方式不变。
#
# 用法：
#   bash tools/ui_audit.sh                # 审计当前工作区
#   bash tools/ui_audit.sh --strict       # 任一项不达标即非零退出（CI / nightly 用）
#   bash tools/ui_audit.sh --update-baseline   # 用当前实测值收紧基线（需同 PR 提交）
set -uo pipefail
cd "$(dirname "$0")/.."

exec python3 tools/ui_audit.py "$@"
