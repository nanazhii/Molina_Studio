#!/usr/bin/env bash
# Bangun proot arm64 STATIS (talloc statis, loader tertanam) dengan NDK dipin. Hasil: _proot_out/.
#   bash scripts/native_proot.sh
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
. "$ROOT/native/pins.env"
# shellcheck source=/dev/null
. "$ROOT/native/proot.pins.env"
: "${PROOT_SHA:?}" "${TALLOC_SHA:?}" "${NDK_VERSION:?}" "${ANDROID_API:?}"

WORK="$ROOT/_proot"; OUT="$ROOT/_proot_out"
rm -rf "$WORK" "$OUT"; mkdir -p "$WORK" "$OUT"
export NATIVE_PINS_FILE="$ROOT/native/pins.sha256" NATIVE_HASH_LOG="$OUT/download-sha256.txt"
: > "$NATIVE_HASH_LOG"

bash "$ROOT/scripts/ensure_ndk.sh" --dest "$WORK/ndk"
NDK="$WORK/ndk/android-ndk-$NDK_VERSION"
BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"
CC="$BIN/aarch64-linux-android${ANDROID_API}-clang"
for t in "$CC" "$BIN/llvm-ar" "$BIN/llvm-strip" "$BIN/llvm-objcopy" "$BIN/llvm-objdump" "$BIN/llvm-readelf"; do
  [ -x "$t" ] || { echo "alat NDK tidak ada: $t" >&2; exit 1; }
done

fetch_pinned() { # repo sha dir
  git init -q "$3"
  git -C "$3" fetch -q --depth 1 "$1" "$2"
  git -C "$3" checkout -q FETCH_HEAD
  [ "$(git -C "$3" rev-parse HEAD)" = "$2" ] || { echo "SHA $3 tidak cocok dengan pin" >&2; exit 1; }
}
fetch_pinned "$PROOT_REPO" "$PROOT_SHA" "$WORK/proot-src"
fetch_pinned "$TALLOC_REPO" "$TALLOC_SHA" "$WORK/talloc-src"

# ---- talloc statis (hanya talloc.c, tanpa waf) ----
T="$WORK/talloc-out"; mkdir -p "$T/include" "$T/lib" "$T/shim"
cat > "$T/shim/replace.h" <<'EOF'
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdint.h>
#include <stdbool.h>
#include <stdarg.h>
#include <stddef.h>
#include <errno.h>
#include <unistd.h>
#include <limits.h>
#include <sys/types.h>
#include <sys/mman.h>
#define _PUBLIC_
#define _PRIVATE_
#define discard_const(ptr) ((void *)((uintptr_t)(ptr)))
#define discard_const_p(type, ptr) ((type *)discard_const(ptr))
#ifndef MIN
#define MIN(a,b) ((a)<(b)?(a):(b))
#endif
#ifndef MAX
#define MAX(a,b) ((a)>(b)?(a):(b))
#endif
#define likely(x) __builtin_expect(!!(x),1)
#define unlikely(x) __builtin_expect(!!(x),0)
#define PRINTF_ATTRIBUTE(a,b) __attribute__((format(__printf__,a,b)))
#define memset_s(d, dl, c, n) memset((d), (c), (n))
EOF
"$CC" -c -O2 -fPIC -I"$T/shim" -I"$WORK/talloc-src/lib/talloc" \
  -DTALLOC_BUILD_VERSION_MAJOR=2 -DTALLOC_BUILD_VERSION_MINOR=4 -DTALLOC_BUILD_VERSION_RELEASE=4 \
  -DHAVE_VA_COPY=1 -DHAVE_CONSTRUCTOR_ATTRIBUTE=1 \
  "$WORK/talloc-src/lib/talloc/talloc.c" -o "$T/talloc.o"
"$BIN/llvm-ar" rcs "$T/lib/libtalloc.a" "$T/talloc.o"
cp "$WORK/talloc-src/lib/talloc/talloc.h" "$T/include/"

# ---- proot statis (loader tertanam; env agar `+=` di GNUmakefile tetap berlaku) ----
cd "$WORK/proot-src/src"
export CPPFLAGS="-I$T/include" CFLAGS="-Wno-unused-parameter -Wno-implicit-function-declaration" \
       LDFLAGS="-static -L$T/lib"
make -j"$(nproc)" CC="$CC" STRIP="$BIN/llvm-strip" OBJCOPY="$BIN/llvm-objcopy" OBJDUMP="$BIN/llvm-objdump" proot

[ -s proot ] || { echo "binary proot tidak terbentuk" >&2; exit 1; }
"$BIN/llvm-strip" -o "$OUT/proot" proot
# Verifikasi: ELF aarch64, tanpa pustaka dinamis (NEEDED kosong).
"$BIN/llvm-readelf" -h "$OUT/proot" | grep -q 'AArch64' || { echo "bukan ELF AArch64" >&2; exit 1; }
if "$BIN/llvm-readelf" -d "$OUT/proot" 2>/dev/null | grep -q 'NEEDED'; then
  echo "proot masih memerlukan pustaka dinamis (bukan statis)" >&2
  "$BIN/llvm-readelf" -d "$OUT/proot" >&2; exit 1
fi

# ---- paket + hash ----
cd "$OUT"
{
  echo "proot_repo=$PROOT_REPO"; echo "proot_sha=$PROOT_SHA"
  echo "talloc_tag=$TALLOC_TAG"; echo "talloc_sha=$TALLOC_SHA"
  echo "ndk=$NDK_VERSION ($NDK_REVISION)"; echo "android_api=$ANDROID_API"
  echo "built=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > BUILD-INFO.txt
tar -czf molina-proot-arm64.tgz proot
sha256sum molina-proot-arm64.tgz proot BUILD-INFO.txt > SHA256SUMS
echo "Selesai: $OUT"; cat SHA256SUMS
