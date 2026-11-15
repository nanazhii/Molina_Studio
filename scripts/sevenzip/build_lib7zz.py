#!/usr/bin/env python3
# [7ZIP-SECCOMP] Membangun lib7zz.so dari rilis resmi 7-Zip: biner statis 7zzs dengan tiga instruksi `svc #0`
# (syscall set_robust_list, diblokir filter seccomp Android 8) diganti `nop`. Hash tarball, biner asli, dan hasil
# diperiksa terhadap scripts/sevenzip/pins.sha256; salah satu tidak cocok = gagal, tidak ada yang ditulis.
#
#   python3 scripts/sevenzip/build_lib7zz.py --out-lib app/src/main/jniLibs/arm64-v8a/lib7zz.so \
#       --out-license app/src/main/assets/licenses/7zip-License.txt [--tarball 7z2605-linux-arm64.tar.xz]
# Idempotent: bila keluaran sudah cocok dengan pin, tidak mengunduh dan tidak menulis apa pun.
import argparse
import hashlib
import io
import os
import sys
import tarfile
import urllib.request

TARBALL = "7z2605-linux-arm64.tar.xz"
URL = "https://github.com/ip7z/7zip/releases/download/26.05/" + TARBALL  # gate:allow sumber resmi 7-Zip, dipin SHA-256
SVC0 = bytes.fromhex("010000d4")  # svc #0 (0xd4000001, little-endian)
NOP = bytes.fromhex("1f2003d5")   # nop  (0xd503201f)
OFFSETS = (0x1CFEBC, 0x1D4FEC, 0x1D5054)


def sha256(b):
    return hashlib.sha256(b).hexdigest()


def read_pins(path):
    pins = {}
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if line and not line.startswith("#"):
                h, name = line.split(None, 1)
                pins[name.strip()] = h.lower()
    return pins


def build(tar_bytes, pins):
    """Kembalikan (lib7zz.so, License.txt) atau lempar SystemExit bila ada hash yang tidak cocok."""
    if sha256(tar_bytes) != pins[TARBALL]:
        sys.exit("hash tarball %s tidak cocok dengan pin" % TARBALL)
    with tarfile.open(fileobj=io.BytesIO(tar_bytes), mode="r:xz") as t:
        orig = t.extractfile("7zzs").read()
        lic = t.extractfile("License.txt").read()
    if sha256(orig) != pins["7zzs.orig"]:
        sys.exit("hash 7zzs asli tidak cocok dengan pin")
    out = bytearray(orig)
    for off in OFFSETS:
        if bytes(out[off:off + 4]) != SVC0:
            sys.exit("instruksi di 0x%x bukan svc #0; biner tidak sesuai" % off)
        out[off:off + 4] = NOP
    if sha256(bytes(out)) != pins["lib7zz.so"]:
        sys.exit("hash lib7zz.so hasil tambalan tidak cocok dengan pin")
    return bytes(out), lic


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--pins", default=os.path.join(os.path.dirname(os.path.abspath(__file__)), "pins.sha256"))
    ap.add_argument("--out-lib", required=True)
    ap.add_argument("--out-license", required=True)
    ap.add_argument("--tarball", help="berkas %s lokal (tanpa unduh)" % TARBALL)
    a = ap.parse_args()
    pins = read_pins(a.pins)
    if (os.path.isfile(a.out_lib) and os.path.isfile(a.out_license)
            and sha256(open(a.out_lib, "rb").read()) == pins["lib7zz.so"]):
        print("lib7zz.so sudah sesuai pin: tidak ada perubahan")
        return
    if a.tarball:
        data = open(a.tarball, "rb").read()
    else:
        with urllib.request.urlopen(URL, timeout=120) as r:
            data = r.read()
    lib, lic = build(data, pins)
    for path, content in ((a.out_lib, lib), (a.out_license, lic)):
        os.makedirs(os.path.dirname(os.path.abspath(path)), exist_ok=True)
        tmp = path + ".tmp"
        with open(tmp, "wb") as f:
            f.write(content)
        os.replace(tmp, path)
    print("lib7zz.so dan lisensi ditulis (hash cocok pin)")


if __name__ == "__main__":
    main()
