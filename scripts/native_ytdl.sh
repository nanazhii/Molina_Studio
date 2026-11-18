#!/usr/bin/env bash
# Bangun runtime yt-dlp arm64 untuk Molina Player: CPython bionic (build resmi upstream Android/android.py),
# quickjs-ng (qjs, NDK dipin) dan zipapp yt-dlp dipin. Semua unduhan diverifikasi SHA-256, sumber git dipin ke commit.
# Hasil: _ytdl_out/molina-ytdl-arm64.tgz (pohon staging: bin/, lib/, yt-dlp), BUILD-INFO.txt, SHA256SUMS.
# Prasyarat CI (lihat native-ytdl.yml): ANDROID_HOME + JDK 17/21, paket build Python untuk host x86_64, cmake.
#   bash scripts/native_ytdl.sh
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
. "$ROOT/native/pins.env"
# shellcheck source=/dev/null
. "$ROOT/native/ytdl.pins.env"
: "${CPYTHON_SHA:?}" "${CPYTHON_TAG:?}" "${CPYTHON_HOST:?}" "${CPYTHON_DEPS:?}" "${CPYTHON_DEPS_BASE:?}" \
  "${QJS_SHA:?}" "${QJS_TAG:?}" "${YTDLP_SHA256:?}" "${YTDLP_VERSION:?}" "${YTDLP_URL:?}" \
  "${NDK_VERSION:?}" "${NDK_REVISION:?}"
ANDROID_API="${ANDROID_API:-26}"
: "${ANDROID_HOME:?ANDROID_HOME wajib ada (SDK Android; android.py memasang NDK-nya sendiri lewat sdkmanager)}"
fail() { echo "GAGAL: $*" >&2; exit 1; }

WORK="$ROOT/_ytdl"; OUT="$ROOT/_ytdl_out"
rm -rf "$WORK" "$OUT"; mkdir -p "$WORK" "$OUT"
export NATIVE_PINS_FILE="$ROOT/native/pins.sha256" NATIVE_HASH_LOG="$OUT/download-sha256.txt"
: > "$NATIVE_HASH_LOG"

fetch_pinned() { # repo sha dir
  git init -q "$3"
  git -C "$3" fetch -q --depth 1 "$1" "$2"
  git -C "$3" checkout -q FETCH_HEAD
  [ "$(git -C "$3" rev-parse HEAD)" = "$2" ] || fail "SHA $3 tidak cocok dengan pin"
}

# ---------- 1. yt-dlp zipapp (data murni Python; sudah memuat yt_dlp_ejs) ----------
bash "$ROOT/scripts/native_wget.sh" "$YTDLP_URL" -O "$OUT/yt-dlp"
[ "$(sha256sum "$OUT/yt-dlp" | cut -d' ' -f1)" = "$YTDLP_SHA256" ] || fail "SHA-256 yt-dlp berbeda dari YTDLP_SHA256"
python3 - "$OUT/yt-dlp" "$YTDLP_VERSION" << 'PY'
import sys, zipfile
z = zipfile.ZipFile(sys.argv[1]); n = set(z.namelist())
ver = z.read("yt_dlp/version.py").decode()
if f"__version__ = '{sys.argv[2]}'" not in ver:
    sys.exit("versi zipapp bukan " + sys.argv[2])
for need in ("yt_dlp_ejs/__init__.py", "yt_dlp_ejs/yt/solver/core.min.js", "__main__.py"):
    if need not in n:
        sys.exit("zipapp tidak memuat " + need)
print("zipapp yt-dlp OK:", sys.argv[2])
PY

# ---------- 2. quickjs-ng (qjs) dengan NDK dipin ----------
bash "$ROOT/scripts/ensure_ndk.sh" --dest "$WORK/ndk"
NDK="$WORK/ndk/android-ndk-$NDK_VERSION"
BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
for t in "$BIN/llvm-strip" "$BIN/llvm-readelf"; do [ -x "$t" ] || fail "alat NDK tidak ada: $t"; done
fetch_pinned "$QJS_REPO" "$QJS_SHA" "$WORK/qjs-src"
cmake -S "$WORK/qjs-src" -B "$WORK/qjs-build" \
  -DCMAKE_TOOLCHAIN_FILE="$NDK/build/cmake/android.toolchain.cmake" \
  -DANDROID_ABI=arm64-v8a -DANDROID_PLATFORM="android-$ANDROID_API" \
  -DCMAKE_BUILD_TYPE=MinSizeRel -DBUILD_SHARED_LIBS=OFF
cmake --build "$WORK/qjs-build" --target qjs_exe -j"$(nproc)"
[ -s "$WORK/qjs-build/qjs" ] || fail "binary qjs tidak terbentuk"
"$BIN/llvm-strip" -o "$OUT/qjs" "$WORK/qjs-build/qjs"
"$BIN/llvm-readelf" -h "$OUT/qjs" | grep -q 'AArch64' || fail "qjs bukan ELF AArch64"
QJS_NEEDED="$("$BIN/llvm-readelf" -d "$OUT/qjs" | grep 'NEEDED' || true)"
echo "$QJS_NEEDED" | grep -q '\[libc\.so\]' || fail "qjs tidak terhubung ke libc.so"
BAD="$(echo "$QJS_NEEDED" | grep -Ev '\[(libc|libm|libdl)\.so\]' || true)"
[ -z "$BAD" ] || fail "qjs butuh pustaka non-sistem: $BAD"

# ---------- 3. CPython bionic (upstream Android/android.py) ----------
fetch_pinned "$CPYTHON_REPO" "$CPYTHON_SHA" "$WORK/cpython"
ANDROID_PY="$WORK/cpython/Android/android.py"
# Daftar dependensi upstream harus identik dengan yang kita pin; bila upstream berubah, build berhenti.
UP_DEPS="$(python3 - "$ANDROID_PY" << 'PY'
import re, sys
s = open(sys.argv[1]).read()
m = re.search(r"def unpack_deps.*?for name_ver in \[(.*?)\]:", s, re.S)
print(" ".join(sorted(re.findall(r'"([^"]+)"', m.group(1)))))
PY
)"
MY_DEPS="$(echo "$CPYTHON_DEPS" | tr ' ' '\n' | sort | tr '\n' ' ' | sed 's/ $//')"
[ "$UP_DEPS" = "$MY_DEPS" ] || fail "dependensi android.py ($UP_DEPS) tidak sama dengan CPYTHON_DEPS ($MY_DEPS)"
# Prefetch ke cache dengan verifikasi SHA-256; android.py memakai berkas yang sudah ada dan tidak mengunduh lagi.
CACHE="$WORK/cpython-deps"; mkdir -p "$CACHE"
for nv in $CPYTHON_DEPS; do
  f="$nv-$CPYTHON_HOST.tar.gz"
  bash "$ROOT/scripts/native_wget.sh" "$CPYTHON_DEPS_BASE/$nv/$f" -O "$CACHE/$f"
done
# android.py: target "build" membuat Python pendamping (cross-build/build) yang WAJIB ada sebelum target host;
# memberi nama host saja melewatinya dan gagal dengan "cross-build/build does not exist".
( cd "$WORK/cpython" && python3 Android/android.py build build )
( cd "$WORK/cpython" && python3 Android/android.py build "$CPYTHON_HOST" --cache-dir "$CACHE" )
( cd "$WORK/cpython" && python3 Android/android.py package "$CPYTHON_HOST" )
PKG="$(ls "$WORK"/cpython/cross-build/"$CPYTHON_HOST"/dist/python-*-"$CPYTHON_HOST".tar.gz 2>/dev/null | head -n1 || true)"
[ -n "$PKG" ] || fail "paket python-*.tar.gz tidak ditemukan"
PREFIX="$WORK/cpython/cross-build/$CPYTHON_HOST/prefix"
PYEXE="$(ls "$PREFIX"/bin/python3.[0-9]* 2>/dev/null | grep -Ev '(-config|\.py)$' | head -n1 || true)"
[ -n "$PYEXE" ] && [ -x "$PYEXE" ] || fail "executable python tidak ada di $PREFIX/bin (make install gagal?)"

# ---------- 4. Susun pohon staging ----------
ST="$OUT/stage"; mkdir -p "$ST/bin" "$ST/lib"
tar -xzf "$PKG" -C "$WORK" --one-top-level=pkg
LIBSRC="$WORK/pkg/prefix/lib" # struktur paket android.py: prefix/lib, prefix/include, Android/
[ -d "$LIBSRC" ] || fail "folder prefix/lib tidak ada di paket ($(ls "$WORK/pkg" | tr '\n' ' '))"
cp -a "$LIBSRC"/. "$ST/lib/"
cp "$PYEXE" "$ST/bin/python3"; "$BIN/llvm-strip" "$ST/bin/python3" || true
cp "$OUT/qjs" "$ST/bin/qjs"
cp "$OUT/yt-dlp" "$ST/yt-dlp"
PYLIB="$(ls "$ST"/lib/libpython3.*.so | head -n1 || true)"
[ -n "$PYLIB" ] || fail "libpython3.x.so tidak ada di staging"
# Pangkas stdlib yang tidak dipakai yt-dlp.
STD="$(ls -d "$ST"/lib/python3.[0-9]* | head -n1)"
for d in test idlelib tkinter turtledemo ensurepip lib2to3 pydoc_data; do rm -rf "$STD/$d"; done
find "$STD" -type d \( -name __pycache__ -o -name 'config-*' \) -prune -exec rm -rf {} +
find "$STD" -type d -name test -prune -exec rm -rf {} +

# ---------- 5. Verifikasi statis (tidak bisa dijalankan di runner x86: perlu linker Android) ----------
"$BIN/llvm-readelf" -h "$ST/bin/python3" | grep -q 'AArch64' || fail "python3 bukan ELF AArch64"
PY_NEEDED="$("$BIN/llvm-readelf" -d "$ST/bin/python3" | grep 'NEEDED' || true)"
echo "$PY_NEEDED" | grep -q "$(basename "$PYLIB")" || fail "python3 tidak terhubung ke $(basename "$PYLIB")"
for m in _ssl _hashlib _sqlite3; do
  ls "$STD"/lib-dynload/"$m"*.so >/dev/null 2>&1 || fail "modul $m tidak ada di lib-dynload"
done
ls "$ST"/lib/libssl*.so "$ST"/lib/libcrypto*.so >/dev/null 2>&1 || fail "libssl/libcrypto tidak ada di staging"

# ---------- 6. Paket + BUILD-INFO + hash ----------
cd "$OUT"
{
  echo "cpython_repo=$CPYTHON_REPO"; echo "cpython_tag=$CPYTHON_TAG"; echo "cpython_sha=$CPYTHON_SHA"
  echo "cpython_host=$CPYTHON_HOST"; echo "cpython_deps=$CPYTHON_DEPS"
  echo "qjs_tag=$QJS_TAG"; echo "qjs_sha=$QJS_SHA"
  echo "ytdlp_version=$YTDLP_VERSION"; echo "ytdlp_sha256=$YTDLP_SHA256"
  echo "ndk_qjs=$NDK_VERSION ($NDK_REVISION)"; echo "android_api_qjs=$ANDROID_API"
  echo "python_needed=$(echo "$PY_NEEDED" | tr -s ' ' | tr '\n' ';')"
  echo "python_runpath=$("$BIN/llvm-readelf" -d "$ST/bin/python3" | grep -E 'RPATH|RUNPATH' | tr -s ' ' || echo none)"
  echo "qjs_needed=$(echo "$QJS_NEEDED" | tr -s ' ' | tr '\n' ';')"
  echo "stage_size_kb=$(du -sk "$ST" | cut -f1)"
  echo "built=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > BUILD-INFO.txt
tar -czf molina-ytdl-arm64.tgz -C "$ST" .
sha256sum molina-ytdl-arm64.tgz yt-dlp qjs BUILD-INFO.txt > SHA256SUMS
echo "Selesai: $OUT"; cat SHA256SUMS; cat BUILD-INFO.txt
