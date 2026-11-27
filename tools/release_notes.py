#!/usr/bin/env python3
"""从 CHANGELOG.md 抽出某个版本的章节，作为 Release 说明（M1-06 配套）。

用法：
    python3 tools/release_notes.py 0.19.0            # 打印到 stdout
    python3 tools/release_notes.py 0.19.0 --out release-notes.md

约定：
  - 章节标题形如 `## [0.19.0] - 2026-11-27`；
  - 找不到对应章节时**不编造内容**：输出提示行并以非零退出，
    由调用方决定是回退到默认说明还是中止发版。
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

REPO_ROOT = pathlib.Path(__file__).resolve().parent.parent
CHANGELOG = REPO_ROOT / "CHANGELOG.md"


def extract(version: str) -> str | None:
    text = CHANGELOG.read_text(encoding="utf-8")
    pattern = r"^(## \[" + re.escape(version) + r"\][^\n]*\n.*?)(?=^## |\Z)"
    match = re.search(pattern, text, re.M | re.S)
    if not match:
        return None
    return match.group(1).strip()


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("version", help="版本号（例 0.19.0）")
    parser.add_argument("--out", help="输出文件（默认打印到 stdout）")
    args = parser.parse_args()

    section = extract(args.version)
    if section is None:
        print(f"::error::CHANGELOG.md 中没有 [{args.version}] 章节", file=sys.stderr)
        return 1

    if args.out:
        pathlib.Path(args.out).write_text(section + "\n", encoding="utf-8")
        print(f"已写出 {args.out}")
    else:
        print(section)
    return 0


if __name__ == "__main__":
    sys.exit(main())
