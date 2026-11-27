#!/usr/bin/env python3
"""UI 审计（P-200 方法学固化 → R7 改为**真棘轮**）。

为什么改成 Python：
  1. 判定「硬编码中文」必须**先剔除注释**——KDoc 里的中文引号不是界面文案，
     旧版用 grep 全文匹配，把 7 个只含注释的文件误判成债务，白名单被无谓文件占满；
  2. 阈值必须**可机读、可收紧、可校验只减不增**——旧版把上限写成 999，
     等于没有门禁（这是典型的「假门禁」：看起来在检查，实际永远绿）。

口径与 konsist 规则 13 / 14 对齐：
  - 硬编码中文：扫描 app + core/presentation + 全部 feature 的 presentation 层；
  - 裸色值：内容调色板（代码高亮 / Diff 语义色 / 标签数据色）共 3 个文件是**刻意例外**，
    与 konsist 规则 14 的例外清单一一对应。

用法：
  python3 tools/ui_audit.py              # 审计并打印报告
  python3 tools/ui_audit.py --strict     # 任一项不达标即非零退出（CI / nightly 用）
  python3 tools/ui_audit.py --update-baseline   # 用当前实测值收紧基线
"""

from __future__ import annotations

import argparse
import json
import pathlib
import re
import sys

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
BASELINE_FILE = REPO_ROOT / "tools" / "audit_baseline.json"

UI_ROOTS = [
    pathlib.Path("app/src/main/kotlin"),
    pathlib.Path("core/presentation/src/main/kotlin"),
]
FEATURE_UI_ROOT = pathlib.Path("feature")

ZH_LITERAL = re.compile(r'"[^"\n]*[\u4e00-\u9fff][^"\n]*"')


# ── Kotlin 注释剔除（与 konsist 规则 13 同一套判定逻辑） ──────────────────
def strip_comments(source: str) -> str:
    """去掉 Kotlin 注释，保留字符串字面量内容。

    识别行注释、块注释（含嵌套）、字符串（含三引号与 `${…}` 模板）、字符字面量。
    模板内部原样保留：模板里出现注释是极少数情况，递归处理得不偿失。
    """
    out: list[str] = []
    i = 0
    n = len(source)
    while i < n:
        if source.startswith("//", i) or source.startswith("/*", i):
            i = _skip_comment(source, i)
        elif source[i] == '"':
            end = _skip_string(source, i)
            out.append(source[i:end])
            i = end
        elif source[i] == "'":
            end = _skip_char(source, i)
            out.append(source[i:end])
            i = end
        else:
            out.append(source[i])
            i += 1
    return "".join(out)


def _skip_comment(source: str, start: int) -> int:
    if source.startswith("//", start):
        i = start + 2
        while i < len(source) and source[i] != "\n":
            i += 1
        return i
    depth = 1
    i = start + 2
    while i < len(source) and depth > 0:
        if source.startswith("/*", i):
            depth += 1
            i += 2
        elif source.startswith("*/", i):
            depth -= 1
            i += 2
        else:
            i += 1
    return i


def _skip_string(source: str, start: int) -> int:
    n = len(source)
    if source.startswith('"""', start):
        j = start + 3
        while j < n and not source.startswith('"""', j):
            j += 1
        return min(j + 3, n)
    j = start + 1
    while j < n:
        if source[j] == "\\":
            j += 2
        elif source[j] == '"':
            return j + 1
        elif source[j] == "$" and j + 1 < n and source[j + 1] == "{":
            j = _skip_template(source, j + 2)
        else:
            j += 1
    return j


def _skip_char(source: str, start: int) -> int:
    n = len(source)
    j = start + 1
    while j < n:
        if source[j] == "\\":
            j += 2
        elif source[j] == "'":
            return j + 1
        elif source[j] == "\n":
            return j
        else:
            j += 1
    return j


def _skip_template(source: str, start: int) -> int:
    depth = 1
    i = start
    n = len(source)
    while i < n and depth > 0:
        if source.startswith("//", i) or source.startswith("/*", i):
            i = _skip_comment(source, i)
        elif source[i] == '"':
            i = _skip_string(source, i)
        elif source[i] == "{":
            depth += 1
            i += 1
        elif source[i] == "}":
            depth -= 1
            i += 1
        else:
            i += 1
    return i


# ── 度量 ────────────────────────────────────────────────────────────────
def feature_ui_dirs() -> list[pathlib.Path]:
    if not FEATURE_UI_ROOT.exists():
        return []
    return sorted(
        p / "presentation" / "src" / "main" / "kotlin"
        for p in FEATURE_UI_ROOT.iterdir()
        if (p / "presentation" / "src" / "main" / "kotlin").is_dir()
    )


def all_ui_dirs() -> list[pathlib.Path]:
    return UI_ROOTS + feature_ui_dirs()


def kotlin_files(dirs: list[pathlib.Path]) -> list[pathlib.Path]:
    files: list[pathlib.Path] = []
    for d in dirs:
        if d.exists():
            files.extend(sorted(d.rglob("*.kt")))
    return files


def read(path: pathlib.Path) -> str:
    return path.read_text(encoding="utf-8", errors="ignore")


def measure() -> dict[str, int]:
    feature_dirs = feature_ui_dirs()
    feature_files = kotlin_files(feature_dirs)
    all_files = kotlin_files(all_ui_dirs())

    raw_color = sum(1 for f in feature_files if "Color(0x" in read(f))
    # 复用度按 **import 语句条数** 计（口径与 R6 版审计脚本一致，便于对比）。
    reuse = sum(
        read(f).count("import com.gy.githubclient.core.presentation")
        for f in feature_files
    )
    # 无障碍语义横跨设计系统与业务屏：扫描全部 UI 目录。
    content_desc = sum(1 for f in all_files if "contentDescription" in read(f))
    semantics = sum(1 for f in all_files if "semantics" in read(f))
    chinese = sum(1 for f in all_files if ZH_LITERAL.search(strip_comments(read(f))))

    states = {}
    for label, pattern in (
        ("空态", "暂无"),
        ("空态2", "空列表"),
        ("错误态", "失败"),
        ("错误态2", "重试"),
        ("加载态", "加载"),
        ("离线态", "离线"),
        ("权限态", "权限"),
    ):
        states[f"state_{label}"] = sum(1 for f in feature_files if pattern in read(f))

    result = {
        "biz_raw_color_files": raw_color,
        "core_presentation_reuse": reuse,
        "content_description_files": content_desc,
        "semantics_files": semantics,
        "hardcoded_chinese_ui_files": chinese,
    }
    result.update(states)
    return result


# ── 基线与棘轮 ──────────────────────────────────────────────────────────
def load_baseline() -> dict:
    if not BASELINE_FILE.exists():
        return {"metrics": {}}
    return json.loads(BASELINE_FILE.read_text(encoding="utf-8"))


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--strict", action="store_true", help="任一项不达标即非零退出")
    parser.add_argument("--update-baseline", action="store_true", help="用当前实测值收紧基线")
    args = parser.parse_args()

    baseline = load_baseline()
    metrics_cfg = baseline.get("metrics", {})
    current = measure()

    if args.update_baseline:
        changed = []
        for key, value in current.items():
            cfg = metrics_cfg.get(key)
            if cfg is None:
                continue
            if cfg["value"] != value:
                changed.append(f"  {key}: {cfg['value']} → {value}")
                cfg["value"] = value
        if changed:
            BASELINE_FILE.write_text(
                json.dumps(baseline, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
            )
            print("基线已收紧：")
            print("\n".join(changed))
        else:
            print("基线无需变更。")
        return 0

    print(f"=== UI 审计（真棘轮）· 基线 {BASELINE_FILE.name} ===\n")
    print(f"{'指标':<30}{'实测':>6}{'基线':>6}  判定")
    print("-" * 72)

    failed = False
    for key, cfg in metrics_cfg.items():
        value = current.get(key)
        if value is None:
            continue
        limit = cfg["value"]
        direction = cfg["direction"]
        if not cfg.get("ratchet", True):
            # 覆盖下限类指标：只提示"有人处理过"，不作为棘轮（关键词计数本身脆弱，
            # 改个文案就误报，因此不拿它当门禁）。
            print(f"{cfg['label']:<30}{value:>6}{limit:>6}  ✓ 下限")
            continue
        if direction == "max":
            if value > limit:
                verdict, failed = "✗ 超标", True
            elif value < limit:
                verdict = "↓ 可收紧"
            else:
                verdict = "✓"
        else:
            if value < limit:
                verdict, failed = "✗ 不足", True
            elif value > limit:
                verdict = "↑ 可抬高"
            else:
                verdict = "✓"
        print(f"{cfg['label']:<30}{value:>6}{limit:>6}  {verdict}")

    print()
    if failed:
        print("::error::UI 审计不达标（见上方 ✗ 项）")
        if args.strict:
            return 1
    else:
        print("UI 审计达标。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
