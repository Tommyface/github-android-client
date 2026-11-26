#!/usr/bin/env python3
"""覆盖率阈值门禁（M0-09）。

为什么不用 kover 内建 rule：内建阈值需要写死 DSL 且不同版本写法易漂移，
而覆盖率报告本身是标准 JaCoCo XML，用脚本解析更可控、报错信息更可读。

用法：
  python3 tools/check_coverage.py --report app/build/reports/kover/report.xml
  python3 tools/check_coverage.py --report <xml> --threshold 65
阈值默认从 gradle.properties 的 `ghcCoverageThreshold` 读取。
"""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_THRESHOLD = 60


def read_threshold() -> int:
    props = REPO_ROOT / "gradle.properties"
    if props.exists():
        for raw in props.read_text(encoding="utf-8").splitlines():
            line = raw.strip()
            if line.startswith("ghcCoverageThreshold="):
                value = line.split("=", 1)[1].strip()
                if value.isdigit():
                    return int(value)
    return DEFAULT_THRESHOLD


def find_report(explicit: str | None) -> Path | None:
    if explicit:
        candidate = Path(explicit)
        return candidate if candidate.exists() else None
    candidates = sorted(REPO_ROOT.glob("**/build/reports/kover/report.xml"))
    return candidates[0] if candidates else None


def line_coverage(report: Path) -> tuple[int, int]:
    """返回 (已覆盖行数, 可覆盖行总数)。兼容 JaCoCo XML 结构。"""
    tree = ET.parse(report)
    covered = 0
    total = 0
    for line in tree.iter("line"):
        mi = int(line.get("mi", "0"))
        ci = int(line.get("ci", "0"))
        mb = int(line.get("mb", "0"))
        cb = int(line.get("cb", "0"))
        covered += ci + cb
        total += mi + ci + mb + cb
    return covered, total


def main() -> int:
    parser = argparse.ArgumentParser(description="覆盖率阈值门禁")
    parser.add_argument("--report", help="kover XML 报告路径")
    parser.add_argument("--threshold", type=int, default=None)
    args = parser.parse_args()

    threshold = args.threshold if args.threshold is not None else read_threshold()
    report = find_report(args.report)
    if report is None:
        print("::error::未找到覆盖率报告（kover XML）。请先执行 ./gradlew koverXmlReport", file=sys.stderr)
        return 1

    covered, total = line_coverage(report)
    if total == 0:
        print("::error::覆盖率报告中没有任何可统计行，报告疑似无效", file=sys.stderr)
        return 1

    percent = covered * 100.0 / total
    print(f"覆盖率报告：{report}")
    print(f"行覆盖：{covered}/{total} = {percent:.2f}%（阈值 {threshold}%）")

    if percent + 1e-9 < threshold:
        print(
            f"::error::行覆盖率 {percent:.2f}% 低于阈值 {threshold}%，门禁不通过",
            file=sys.stderr,
        )
        return 1
    print("覆盖率门禁通过。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
