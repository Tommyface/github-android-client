#!/usr/bin/env python3
"""从 APK 签名块里取签名证书 SHA-256（不依赖 apksigner / Android SDK）。

为什么要有这个脚本
------------------
`version.json.fingerprint` 的语义是**签名证书指纹**，由 `release.yml` 从
`apksigner verify --print-certs` 的文本输出里 grep 出来。文本格式是外部契约：
apksigner 换个输出措辞，正则就失配，写进产物的指纹就成了错的——而且不会有任何报错。
本脚本从 APK 的签名块（v2 / v3）直接读证书，给发版链路提供一条独立路径。

用法
----
    python3 tools/verify_apk_cert.py <apk>
    python3 tools/verify_apk_cert.py <apk> --expect e86de404352ba7a1...

`--expect` 不一致时退出码 1（用于 release.yml 与人工复核）。
输出指纹为小写、无冒号 64 位 hex，与 `ApkSignatureVerifier.RELEASE_CERT_SHA256` 同格式。

边界（不要当成签名校验器）
------------------------
- 只解析 `APK Signing Block` 里 v2(0x7109871a) / v3(0x1b93ad61) 的证书，**不验证签名本身**；
- 不支持 v1（JAR 签名 / `META-INF/*.RSA`）——本项目的正式产物是 v2 签名；
- 证书字节取自 v2/v3 的 `signed data → certificates`，其内部 length prefix 是
  **uint32 little-endian**（AOSP 的 "length-prefixed" 即此），不是 protobuf varint。
"""

from __future__ import annotations

import hashlib
import struct
import sys

V2_BLOCK_ID = 0x7109871A
V3_BLOCK_ID = 0x1B93AD61
SIG_BLOCK_MAGIC = b"APK Sig Block 42"


def _lp(buf: bytes, pos: int) -> tuple[bytes, int]:
    """读一个 uint32 LE 长度前缀的片段。"""
    if pos + 4 > len(buf):
        raise ValueError("length prefix 越界")
    n = struct.unpack("<I", buf[pos : pos + 4])[0]
    end = pos + 4 + n
    if end > len(buf):
        raise ValueError(f"片段长度 {n} 超出剩余 {len(buf) - pos - 4} 字节")
    return buf[pos + 4 : end], end


def _seq(buf: bytes) -> list[bytes]:
    """把一段重复的 length-prefixed 元素拆开。"""
    out: list[bytes] = []
    pos = 0
    while pos < len(buf):
        item, pos = _lp(buf, pos)
        out.append(item)
    return out


def signing_blocks(apk: bytes) -> dict[int, bytes]:
    """定位 APK Signing Block，按 id 返回各 scheme 的内容。"""
    eocd = apk.rfind(b"PK\x05\x06")
    if eocd < 0:
        raise ValueError("不是有效 APK/ZIP：找不到 EOCD")
    cd_offset = struct.unpack("<I", apk[eocd + 16 : eocd + 20])[0]
    if apk[cd_offset - 16 : cd_offset] != SIG_BLOCK_MAGIC:
        raise ValueError("没有 APK Signing Block（可能是 v1-only 签名产物）")
    size_tail = struct.unpack("<Q", apk[cd_offset - 24 : cd_offset - 16])[0]
    start = cd_offset - size_tail - 8
    size_head = struct.unpack("<Q", apk[start : start + 8])[0]
    if size_head != size_tail:
        raise ValueError(f"签名块长度头尾不一致：{size_head} vs {size_tail}")

    blocks: dict[int, bytes] = {}
    pos, end = start + 8, cd_offset - 24
    while pos < end:
        pair_len = struct.unpack("<Q", apk[pos : pos + 8])[0]
        pos += 8
        block_id = struct.unpack("<I", apk[pos : pos + 4])[0]
        pos += 4
        blocks[block_id] = apk[pos : pos + pair_len - 4]
        pos += pair_len - 4
    return blocks


def cert_digests(apk_path: str) -> list[tuple[str, str]]:
    """返回 [(scheme, sha256hex)]，一个 scheme 一个签名者一条记录。"""
    with open(apk_path, "rb") as fh:
        blocks = signing_blocks(fh.read())

    found: list[tuple[str, str]] = []
    for block_id, scheme in ((V2_BLOCK_ID, "v2"), (V3_BLOCK_ID, "v3")):
        raw = blocks.get(block_id)
        if raw is None:
            continue
        signers_blob, _ = _lp(raw, 0)
        for signer in _seq(signers_blob):
            signed_data, _ = _lp(signer, 0)
            _digests, pos = _lp(signed_data, 0)
            certs, _ = _lp(signed_data, pos)
            for der in _seq(certs):
                found.append((scheme, hashlib.sha256(der).hexdigest()))
    return found


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        print(__doc__.strip().split("\n")[0], file=sys.stderr)
        print("用法：verify_apk_cert.py <apk> [--expect SHA256]", file=sys.stderr)
        return 2

    apk_path = argv[1]
    expected = None
    if "--expect" in argv:
        idx = argv.index("--expect")
        if idx + 1 >= len(argv):
            print("::error::--expect 缺少取值", file=sys.stderr)
            return 2
        expected = argv[idx + 1].strip().lower().replace(":", "")

    try:
        digests = cert_digests(apk_path)
    except (OSError, ValueError, struct.error) as exc:
        print(f"::error::解析 {apk_path} 失败：{exc}", file=sys.stderr)
        return 1

    if not digests:
        print(f"::error::{apk_path} 的签名块里没有 v2/v3 证书", file=sys.stderr)
        return 1

    for scheme, sha in digests:
        print(f"{apk_path}\t{scheme}\tcert-sha256={sha}")

    if expected is None:
        return 0

    actual = digests[0][1]
    if actual != expected:
        print(
            f"::error::证书指纹不一致：产物 {actual} vs 期望 {expected}",
            file=sys.stderr,
        )
        return 1
    print(f"::notice::证书指纹与期望一致：{actual}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))