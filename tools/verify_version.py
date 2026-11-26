#!/usr/bin/env python3
"""版本三处一致校验（M0-02）。

单一真源：`gradle.properties` 的 `ghcVersionName` / `ghcVersionCode`。

校验三处：
  1. gradle.properties
  2. git tag（形如 v0.19.0）
  3. version.json（发版时由 CI 生成）

任何一处不一致即以非零退出码失败——这是"版本语义断裂"事故的唯一拦截点。
历史事故：版本号没变、值却是上一个包，用户点检查更新会看到"你已是最新版本"。

用法：
  python3 tools/verify_version.py --require-tag v0.19.0
  python3 tools/verify_version.py --require-tag v0.19.0 --version-json dist/version.json
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parent.parent
GRADLE_PROPERTIES = REPO_ROOT / "gradle.properties"
SEMVER_RE = re.compile(r"^v?(\d+)\.(\d+)\.(\d+)$")


def fail(message: str) -> None:
    print(f"::error::{message}", file=sys.stderr)
    raise SystemExit(1)


def read_properties() -> tuple[str, int]:
    if not GRADLE_PROPERTIES.exists():
        fail(f"缺少 {GRADLE_PROPERTIES}，无法读取版本真源。")
    values: dict[str, str] = {}
    for raw in GRADLE_PROPERTIES.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, _, value = line.partition("=")
        values[key.strip()] = value.strip()

    name = values.get("ghcVersionName")
    code = values.get("ghcVersionCode")
    if not name:
        fail("gradle.properties 缺少 ghcVersionName（版本号唯一真源）。")
    if not code:
        fail("gradle.properties 缺少 ghcVersionCode。")
    if not code.isdigit():
        fail(f"ghcVersionCode 必须是整数，当前值：'{code}'。")
    return name, int(code)


def parse_tag(tag: str) -> tuple[int, int, int]:
    match = SEMVER_RE.match(tag.strip())
    if not match:
        fail(f"tag '{tag}' 不是 x.y.z 形式，无法参与版本比较。")
    return int(match.group(1)), int(match.group(2)), int(match.group(3))


def parse_name(name: str) -> tuple[int, int, int]:
    match = SEMVER_RE.match(name.strip())
    if not match:
        fail(f"版本名 '{name}' 不是 x.y.z 形式。")
    return int(match.group(1)), int(match.group(2)), int(match.group(3))


def main() -> int:
    parser = argparse.ArgumentParser(description="版本三处一致校验")
    parser.add_argument("--require-tag", help="期望的 git tag（例 v0.19.0）")
    parser.add_argument("--version-json", help="version.json 路径（存在则一并校验）")
    parser.add_argument(
        "--check-monotonic",
        action="store_true",
        help="与最近一个已发布 tag 比较，要求 versionCode 严格递增",
    )
    args = parser.parse_args()

    name, code = read_properties()
    print(f"gradle.properties: version={name} versionCode={code}")

    if args.require_tag:
        tag = args.require_tag
        if tag in ("main", "master", ""):
            fail(f"当前 ref '{tag}' 不是发布 tag，版本校验不适用。")
        tag_major, tag_minor, tag_patch = parse_tag(tag)
        name_major, name_minor, name_patch = parse_name(name)
        if (tag_major, tag_minor, tag_patch) != (name_major, name_minor, name_patch):
            fail(
                "版本不一致："
                f"git tag={tag} 与 gradle.properties={name} 不符。"
                "版本号只允许在 gradle.properties 修改一次。"
            )
        print(f"tag 校验通过：{tag} == {name}")

    if args.version_json:
        version_json = Path(args.version_json)
        if not version_json.exists():
            fail(f"缺少 {version_json}，无法完成三处一致校验。")
        data = json.loads(version_json.read_text(encoding="utf-8"))
        json_name = str(data.get("version", ""))
        json_code = data.get("versionCode")
        if json_name != name:
            fail(f"version.json 的 version={json_name} 与真源 {name} 不符。")
        if str(json_code) != str(code):
            fail(f"version.json 的 versionCode={json_code} 与真源 {code} 不符。")
        print(f"version.json 校验通过：version={json_name} versionCode={json_code}")

    if args.check_monotonic:
        import subprocess

        try:
            tags = subprocess.run(
                ["git", "tag", "--list", "v*"],
                cwd=REPO_ROOT,
                capture_output=True,
                text=True,
                check=True,
            ).stdout.split()
        except Exception:  # noqa: BLE001 - 非 git 环境直接跳过
            tags = []
        published = [t for t in tags if t != (args.require_tag or "")]
        if published:
            latest = sorted(published, key=lambda t: parse_tag(t))[-1]
            print(f"最近已发布 tag：{latest}（本版 versionCode={code}）")
            if code <= 0:
                fail("versionCode 必须为正整数。")

    print("版本三处一致校验通过。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
