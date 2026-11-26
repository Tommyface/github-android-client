#!/usr/bin/env python3
"""从 Conventional Commits 生成 CHANGELOG 片段（M1-06）。

约定：
  - 提交信息格式：`type(scope): 摘要`，type ∈ feat/fix/docs/refactor/test/chore/ci/perf/build；
  - 破坏性变更：正文含 `BREAKING CHANGE:` 或标题带 `!`；
  - 生成的章节插入到 CHANGELOG.md 的 `## [Unreleased]` 之后（若存在），否则置顶。

用法：
  python3 tools/generate_changelog.py --version 0.19.0 --since v0.17.0
  python3 tools/generate_changelog.py --version 0.19.0 --since v0.17.0 --dry-run
"""

from __future__ import annotations

import argparse
import re
import subprocess
import sys
from collections import OrderedDict
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
CHANGELOG = REPO_ROOT / "CHANGELOG.md"

COMMIT_PATTERN = re.compile(r"^(?P<type>[a-zA-Z]+)(?:\((?P<scope>[^)]*)\))?(?P<breaking>!)?: (?P<subject>.+)$")

SECTION_ORDER = ["feat", "fix", "perf", "refactor", "docs", "test", "build", "ci", "chore"]
SECTION_TITLES = {
    "feat": "新增",
    "fix": "修复",
    "perf": "性能",
    "refactor": "重构",
    "docs": "文档",
    "test": "测试",
    "build": "构建",
    "ci": "CI",
    "chore": "杂项",
}


def git(*args: str) -> str:
    return subprocess.run(
        ["git", *args],
        cwd=REPO_ROOT,
        capture_output=True,
        text=True,
        check=True,
    ).stdout


def commits_since(since: str | None) -> list[tuple[str, str]]:
    rng = f"{since}..HEAD" if since else "HEAD"
    raw = git("log", rng, "--pretty=format:%H%x1f%s%x1f%b%x1e")
    result: list[tuple[str, str]] = []
    for record in raw.split("\x1e"):
        record = record.strip("\n")
        if not record:
            continue
        parts = record.split("\x1f")
        if len(parts) < 2:
            continue
        sha = parts[0]
        subject = parts[1]
        body = parts[2] if len(parts) > 2 else ""
        result.append((sha, subject if not body else f"{subject}\n\n{body}"))
    return result


def render(commits: list[tuple[str, str]]) -> str:
    buckets: dict[str, list[str]] = {}
    breaking: list[str] = []
    for sha, message in commits:
        first_line = message.splitlines()[0].strip()
        match = COMMIT_PATTERN.match(first_line)
        if not match:
            continue
        ctype = match.group("type").lower()
        scope = match.group("scope")
        subject = match.group("subject").strip()
        is_breaking = bool(match.group("breaking")) or "BREAKING CHANGE" in message
        entry = f"- {subject}" + (f"（{scope}）" if scope else "")
        buckets.setdefault(ctype, []).append(entry)
        if is_breaking:
            breaking.append(entry)

    lines: list[str] = []
    if breaking:
        lines.append("### ⚠ 破坏性变更")
        lines.extend(breaking)
        lines.append("")
    for ctype in SECTION_ORDER:
        items = buckets.get(ctype)
        if not items:
            continue
        lines.append(f"### {SECTION_TITLES.get(ctype, ctype)}")
        lines.extend(sorted(set(items)))
        lines.append("")
    return "\n".join(lines).rstrip() + "\n" if lines else ""


def main() -> int:
    parser = argparse.ArgumentParser(description="从 Conventional Commits 生成 CHANGELOG")
    parser.add_argument("--version", required=True, help="版本号（不含 v 前缀）")
    parser.add_argument("--since", help="起始 tag（不含该 tag 之后的提交）")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    body = render(commits_since(args.since))
    if not body:
        print("没有可归档的提交，未改动 CHANGELOG。")
        return 0

    section = f"## [{args.version}] - {args.version}\n\n{body}"
    if args.dry_run:
        print(section)
        return 0

    existing = CHANGELOG.read_text(encoding="utf-8") if CHANGELOG.exists() else "# 更新日志\n"
    marker = "## [Unreleased]"
    if marker in existing:
        head, _, tail = existing.partition(marker)
        updated = f"{head}{marker}\n\n{section}\n{tail.lstrip()}"
    else:
        lines = existing.splitlines()
        updated = "\n".join(lines[:1] + ["", section] + lines[1:]).rstrip() + "\n"
    CHANGELOG.write_text(updated, encoding="utf-8")
    print(f"CHANGELOG 已更新：{args.version}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
