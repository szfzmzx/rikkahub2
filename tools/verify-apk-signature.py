#!/usr/bin/env python3
"""校验 APK 的 v2 签名证书指纹（不需要 Android SDK / apksigner）。

用法：
    python3 tools/verify-apk-signature.py rikkahub-me-3.1.2-me.apk

输出里 `cert_sha256` 应该和发版流水线 "Verify signature" 步骤里
apksigner 打印的 `certificate SHA-256 digest` 一致。
本个人版所有 release 都必须是同一个值，否则手机上无法覆盖升级。
"""
import hashlib
import struct
import sys

MAGIC = b"APK Sig Block 42"
V2_BLOCK_ID = 0x7109871A


def u32(buf, off):
    return struct.unpack_from("<I", buf, off)[0]


def u64(buf, off):
    return struct.unpack_from("<Q", buf, off)[0]


def find_signing_block(data):
    eocd = data.rfind(b"PK\x05\x06")
    if eocd < 0:
        raise SystemExit("找不到 EOCD，可能不是 zip/apk")
    cd_offset = u32(data, eocd + 16)
    if data[cd_offset - 16:cd_offset] != MAGIC:
        raise SystemExit("找不到 APK Signing Block（v2 及以上签名）")
    size2 = u64(data, cd_offset - 24)
    start = cd_offset - (size2 + 8)
    if u64(data, start) != size2:
        raise SystemExit("signing block 长度字段不一致")
    return start + 8, cd_offset - 24


def find_pair(data, start, end, want_id):
    off = start
    while off < end:
        length = u64(data, off)
        pair_id = u32(data, off + 8)
        if pair_id == want_id:
            return data[off + 12:off + 8 + length]
        off += 8 + length
    return None


def first_cert(value):
    off = 0
    u32(value, off)  # signers sequence 长度
    off += 4
    signer_len = u32(value, off)
    off += 4
    signer_end = off + signer_len

    sd_len = u32(value, off)  # signed data
    off += 4
    sd_end = off + sd_len

    digests_len = u32(value, off)
    off += 4 + digests_len

    certs_len = u32(value, off)
    off += 4
    certs_end = off + certs_len
    if not (off < certs_end <= sd_end <= signer_end):
        raise SystemExit("证书结构解析失败")

    cert_len = u32(value, off)
    off += 4
    return value[off:off + cert_len]


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    data = open(sys.argv[1], "rb").read()
    start, end = find_signing_block(data)
    v2 = find_pair(data, start, end, V2_BLOCK_ID)
    if v2 is None:
        raise SystemExit("没有 v2 签名块")
    cert = first_cert(v2)
    print(f"cert_der_len: {len(cert)}")
    print(f"cert_sha256: {hashlib.sha256(cert).hexdigest()}")


if __name__ == "__main__":
    main()
