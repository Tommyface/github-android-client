#!/usr/bin/env python3
"""覆盖率阈值门禁（M0-09）。

为什么不用 kover 内建 rule：内建阈值需要写死 DSL 且不同版本写法易漂移，
而覆盖率报告本身是标准 JaCoCo XML，用脚本解析更可控、报错信息更可读。

**聚合口径**：kover 为**每个模块**各生成一份 XML（`koverXmlReport`），
只取其中一份会把「UI 多的 app 模块」当成全项目覆盖率（实测仅 8.68%，严重失真）。
本脚本因此聚合**全部模块**的报告，并按 (package, sourcefile, line) 去重，
得到全项目行覆盖率。

阈值来自 `gradle.properties` 的 `ghcCoverageThreshold`：
  - 值 > 0：低于该值即失败（棘轮式，基线只升不降）；
  - 值 = 0：处于**未校准**状态，只打印实测值并提示校准，不阻塞 CI。

用法：
  python3 tools/check_coverage.py                       # 聚合全部模块
  python3 tools/check_coverage.py --update-baseline     # 把实测值写回 gradle.properties
  python3 tools/check_coverage.py --report <xml>        # 只看指定报告
"""

from __future__ import annotations

import argparse
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
GRADLE_PROPERTIES = REPO_ROOT / "gradle.properties"
UNCALIBRATED = 0


def read_threshold() -> int:
    if not GRADLE_PROPERTIES.exists():
        return UNCALIBRATED
    for raw in GRADLE_PROPERTIES.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if line.startswith("ghcCoverageThreshold="):
            value = line.split("=", 1)[1].strip()
            if value.isdigit():
                return int(value)
    return UNCALIBRATED


def write_threshold(value: int) -> None:
    lines = GRADLE_PROPERTIES.read_text(encoding="utf-8").splitlines()
    out = []
    for line in lines:
        if line.strip().startswith("ghcCoverageThreshold="):
            out.append(f"ghcCoverageThreshold={value}")
        else:
            out.append(line)
    GRADLE_PROPERTIES.write_text("\n".join(out) + "\n", encoding="utf-8")


def find_reports(explicit: str | None) -> list[Path]:
    if explicit:
        candidate = Path(explicit)
        return [candidate] if candidate.exists() else []
    return sorted(REPO_ROOT.glob("**/build/reports/kover/report.xml"))


def aggregate(reports: list[Path]) -> tuple[int, int, int]:
    """返回 (已覆盖行, 可覆盖行, 参与统计的文件数)。按 (package, file, line) 去重。"""
    seen: set[tuple[str, str, str]] = set()
    covered = 0
    total = 0
    files = 0
    for report in reports:
        try:
            root = ET.parse(report).getroot()
        except ET.ParseError:
            continue
        for package in root.iter("package"):
            package_name = package.get("name", "")
            for source in package.iter("sourcefile"):
                file_name = source.get("name", "")
                files += 1
                for line in source.iter("line"):
                    key = (package_name, file_name, line.get("nr", ""))
                    if key in seen:
                        continue
                    seen.add(key)
                    mi = int(line.get("mi", "0"))
                    ci = int(line.get("ci", "0"))
                    mb = int(line.get("mb", "0"))
                    cb = int(line.get("cb", "0"))
                    covered += ci + cb
                    total += mi + ci + mb + cb
    return covered, total, files


def main() -> int:
    parser = argparse.ArgumentParser(description="覆盖率阈值门禁（跨模块聚合）")
    parser.add_argument("--report", help="只看指定的 kover XML 报告")
    parser.add_argument("--update-baseline", action="store_true", help="把实测值写回 gradle.properties")
    parser.add_argument("--threshold", type=int, default=None)
    args = parser.parse_args()

    threshold = args.threshold if args.threshold is not None else read_threshold()
    reports = find_reports(args.report)
    if not reports:
        print("::error::未找到 kover XML 报告。请先执行 ./gradlew koverXmlReport", file=sys.stderr)
        return 1

    covered, total, files = aggregate(reports)
    if total == 0:
        print("::error::覆盖率报告中没有任何可统计行，报告疑似无效", file=sys.stderr)
        return 1

    percent = covered * 100.0 / total
    print(f"聚合报告数：{len(reports)}（去重后文件 {files} 个）")
    for report in reports:
        print(f"  · {report.relative_to(REPO_ROOT)}")
    print(f"全项目行覆盖：{covered}/{total} = {percent:.2f}%（阈值 {threshold}%）")

    if args.update_baseline:
        # 校准：向下取整到整数，并留 1 个百分点余量，避免浮点抖动导致假失败
        calibrated = max(0, int(percent) - 1)
        write_threshold(calibrated)
        print(f"已将基线写回 gradle.properties：ghcCoverageThreshold={calibrated}")
        return 0

    if threshold <= UNCALIBRATED:
        print(
            "::warning::覆盖率基线尚未校准（ghcCoverageThreshold=0）。"
            "本次仅打印实测值；请执行 `python3 tools/check_coverage.py --update-baseline` 校准后提交。",
        )
        return 0

    if percent + 1e-9 < threshold:
        print(
            f"::error::全项目行覆盖率 {percent:.2f}% 低于阈值 {threshold}%，门禁不通过。"
            "请补测试而不是调高阈值；确需调整请在 PR 中说明理由。",
            file=sys.stderr,
        )
        return 1
    print("覆盖率门禁通过。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
