#!/usr/bin/env bash
# UI 审计脚本（P-200 评审方法学固化）。
#
# 目的：让「UI 是否达标」变成**可重复执行的审计**，而不是每次评审靠人肉看图。
# 产出：每项给出一个数字 + 判定，供 PR / 里程碑自检。
#
# 用法：
#   bash tools/ui_audit.sh            # 审计当前工作区
#   bash tools/ui_audit.sh --strict   # 任一项不达标即非零退出（可接入 CI）
set -uo pipefail
cd "$(dirname "$0")/.."

STRICT=0
[ "${1:-}" = "--strict" ] && STRICT=1

UI_DIRS=(app/src/main/kotlin core/presentation/src/main/kotlin)
FEATURE_UI=(feature/*/presentation/src/main/kotlin)

fail=0
report() { # 名称 数值 方向(max/min) 阈值 说明
  local name="$1" value="$2" dir="$3" threshold="$4" desc="$5"
  local verdict="✓"
  if [ "$dir" = "max" ]; then
    if [ "$value" -gt "$threshold" ]; then verdict="✗ 超标"; fail=1; fi
  else
    if [ "$value" -lt "$threshold" ]; then verdict="✗ 不足"; fail=1; fi
  fi
  printf '%-28s %6s   %-6s %s\n' "$name" "$value" "$verdict" "$desc"
}

count_files_with() { # 目录数组 模式
  local pattern="$1"; shift
  local dirs=("$@")
  local n=0
  for d in "${dirs[@]}"; do
    [ -d "$d" ] || continue
    n=$((n + $(grep -rl "$pattern" --include="*.kt" "$d" 2>/dev/null | wc -l)))
  done
  echo "$n"
}

echo "=== UI 审计 · $(date +%Y-%m-%d) ==="
echo
printf '%-28s %6s   %-6s %s\n' "指标" "数值" "判定" "说明"
echo "-----------------------------------------------------------------"

# 1) 令牌集中度：业务屏出现裸色值 = 不合规
BIZ_RAW=$(count_files_with "Color(0x" "${FEATURE_UI[@]}")
report "业务屏裸色值文件数" "$BIZ_RAW" max 0 "应为 0（色值只允许出现在 theme 包）"

# 2) 组件复用度
REUSE=$(grep -rn "import com.gy.githubclient.core.presentation" --include="*.kt" "${FEATURE_UI[@]}" 2>/dev/null | wc -l)
report "core/presentation 复用处数" "$REUSE" min 10 "设计系统被真正使用的程度"

# 3) 状态覆盖（六态）
for pair in "空态:暂无" "空态2:空列表" "错误态:失败" "错误态2:重试" "加载态:加载" "离线态:离线" "权限态:权限"; do
  label="${pair%%:*}"; pattern="${pair##*:}"
  n=$(count_files_with "$pattern" "${FEATURE_UI[@]}")
  report "$label 文件数" "$n" min 1 "该状态至少应在一个界面被处理"
done

# 4) 无障碍语义
CD=$(count_files_with "contentDescription" "${FEATURE_UI[@]}")
report "含 contentDescription 文件数" "$CD" min 3 "图标按钮/可点击元素需有描述"

SEM=$(count_files_with "semantics" "${FEATURE_UI[@]}")
report "使用 semantics 文件数" "$SEM" min 1 "列表/进度需语义化"

# 5) 字符串化（存量白名单见 konsist 规则 13）
ZH=$(python3 - <<'PY'
import pathlib, re
roots = [pathlib.Path('app/src/main/kotlin'), pathlib.Path('core/presentation/src/main/kotlin')]
roots += list(pathlib.Path('feature').glob('*/presentation/src/main/kotlin'))
zh = re.compile('"[^"]*[\u4e00-\u9fff][^"]*"')
n = 0
for r in roots:
    if not r.exists():
        continue
    for f in r.rglob('*.kt'):
        if zh.search(f.read_text(encoding='utf-8', errors='ignore')):
            n += 1
print(n)
PY
)
report "硬编码中文 UI 文件数" "$ZH" max 999 "存量棘轮白名单内；每轮应减少"

echo
if [ "$STRICT" -eq 1 ] && [ "$fail" -ne 0 ]; then
  echo "::error::UI 审计未达标（见上方 ✗ 项）"
  exit 1
fi
echo "（--strict 模式下，不达标项会返回非零退出码）"
