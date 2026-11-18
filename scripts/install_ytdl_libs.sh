#!/usr/bin/env bash
# Pasang runtime yt-dlp arm64 dari Release dipin ke modul player:
#  - jniLibs/arm64-v8a : lib*.so (diekstrak installer; executable python/qjs dinamai libX_exec.so)
#  - assets/ytdl       : ytdl-data.zip (stdlib Python, lib-dynload, zipapp yt-dlp) -> diekstrak ke filesDir saat runtime
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PIN="$ROOT/native/ytdl-runtime.pin.env"
if [ ! -s "$PIN" ]; then
  echo "::warning::$PIN belum ada; APK dibangun tanpa yt-dlp."; exit 0
fi
. "$PIN"
T="${RUNNER_TEMP:-/tmp}/ytdl-install"; rm -rf "$T"; mkdir -p "$T/x"
TGZ="${YTDL_TGZ_LOCAL:-}"
if [ -z "$TGZ" ]; then
  gh release download "$YTDL_NATIVE_TAG" --repo "$GITHUB_REPOSITORY" --pattern molina-ytdl-arm64.tgz --dir "$T"
  TGZ="$T/molina-ytdl-arm64.tgz"
fi
echo "$YTDL_NATIVE_TGZ_SHA256  $TGZ" | sha256sum -c -
tar -xzf "$TGZ" -C "$T/x"
S="$T/x"
JNI="$ROOT/player/src/main/jniLibs/arm64-v8a"; AST="$ROOT/player/src/main/assets/ytdl"
mkdir -p "$JNI" "$AST"
install -m 0755 "$S/bin/python3" "$JNI/libpython3_exec.so"
install -m 0755 "$S/bin/qjs" "$JNI/libqjs_exec.so"
for f in libpython3.14.so libssl_python.so libcrypto_python.so libsqlite3_python.so; do
  [ -f "$S/lib/$f" ] || { echo "::error::$f tidak ada di artefak"; exit 1; }
  install -m 0755 "$S/lib/$f" "$JNI/$f"
done
python3 - "$S" "$AST/ytdl-data.zip" "$YTDL_NATIVE_TGZ_SHA256" << 'PY'
import os, sys, zipfile
src, out, sha = sys.argv[1], sys.argv[2], sys.argv[3]
std = os.path.join(src, "lib", "python3.14")
if not os.path.isdir(std): sys.exit("stdlib python3.14 tidak ada")
skip_dyn = ("_test", "_xxtestfuzz", "_ctypes_test")
n = 0
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED, compresslevel=9) as z:
    z.writestr("STAMP", sha + "\n")
    # molina-ytdl: zipapp diekstrak ke ytdlp/ agar Python bisa menyimpan bytecode (.pyc); dari zipapp tidak bisa
    with zipfile.ZipFile(os.path.join(src, "yt-dlp")) as za:
        for zi in za.infolist():
            if not zi.filename.endswith("/"):
                z.writestr("ytdlp/" + zi.filename, za.read(zi.filename))
    for dp, dns, fns in os.walk(std):
        dns[:] = sorted(d for d in dns if d != "__pycache__")
        for fn in sorted(fns):
            if os.path.basename(dp) == "lib-dynload" and fn.startswith(skip_dyn):
                continue
            full = os.path.join(dp, fn)
            z.write(full, "py/lib/python3.14/" + os.path.relpath(full, std)); n += 1
print("ytdl-data.zip: %d berkas, %.1f MB" % (n, os.path.getsize(out) / 1048576))
PY
ls -l "$JNI"
