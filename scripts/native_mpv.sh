#!/usr/bin/env bash
# Bangun libmpv + libplayer (JNI) arm64 dengan skrip build upstream di third_party/mpv-build (tidak diubah).
# Yang ditambahkan Molina: NDK dipin (native/pins.env) dan unduhan tarball terverifikasi SHA-256
# (native/pins.sha256) lewat native_wget.sh, menggantikan wget upstream.
# Hasil: _mpv_out/ (molina-mpv-arm64.tgz berisi arm64-v8a/*.so).
#   bash scripts/native_mpv.sh
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
. "$ROOT/native/pins.env"
: "${NDK_VERSION:?}" "${NDK_REVISION:?}"

B="$ROOT/third_party/mpv-build"
OUT="$ROOT/_mpv_out"
rm -rf "$OUT"; mkdir -p "$OUT"
export NATIVE_PINS_FILE="$ROOT/native/pins.sha256" NATIVE_HASH_LOG="$OUT/download-sha256.txt"
: > "$NATIVE_HASH_LOG"

fail() { echo "native_mpv: $*" >&2; exit 1; }

for f in "$B/buildall.sh" "$B/include/depinfo.sh" "$B/include/download-deps.sh" \
         "$ROOT/player/src/main/jni/Android.mk" "$ROOT/scripts/native_wget.sh" "$ROOT/scripts/ensure_ndk.sh"; do
  [ -s "$f" ] || fail "berkas wajib tidak ada: $f"
done

# NDK yang diminta skrip upstream harus sama dengan pin Molina.
( # shellcheck source=/dev/null
  . "$B/include/depinfo.sh"
  [ "$v_ndk" = "$NDK_VERSION" ] && [ "$v_ndk_n" = "$NDK_REVISION" ] ) \
  || fail "NDK di depinfo.sh upstream berbeda dari native/pins.env"

# upstream mencari NDK di <buildscripts>/sdk/android-ndk-<v_ndk>; ensure_ndk.sh membuat folder bernama sama.
bash "$ROOT/scripts/ensure_ndk.sh" --dest "$B/sdk"
NDK="$B/sdk/android-ndk-$NDK_VERSION"
[ -x "$NDK/ndk-build" ] || fail "ndk-build tidak ada di $NDK"

# Semua unduhan tarball upstream lewat $WGET -> native_wget.sh (HTTPS, SHA-256 dipin, URL tak dipin ditolak).
export WGET="bash $ROOT/scripts/native_wget.sh"

cd "$B"
IN_CI=1 ./include/download-deps.sh
./buildall.sh --only-deps mpv --arch arm64
./buildall.sh -n mpv --arch arm64
[ -s "$B/prefix/arm64/lib/libmpv.so" ] || fail "libmpv.so arm64 tidak terbentuk"

# Bagian JNI (libplayer.so) + salinan pustaka prebuilt ke satu folder, sama seperti langkah ndk-build upstream.
LIBS="$OUT/libs"
PREFIX64="$B/prefix/arm64" "$NDK/ndk-build" -C "$ROOT/player/src/main" -j"$(nproc)" \
  NDK_OUT="$OUT/obj" NDK_LIBS_OUT="$LIBS"

A="$LIBS/arm64-v8a"
for l in libmpv.so libplayer.so libavcodec.so libc++_shared.so; do
  [ -s "$A/$l" ] || fail "$l tidak ada di $A"
done

# Bukti rename: simbol JNI harus berpaket Molina, tidak boleh ada simbol paket lama.
NM="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-nm"
syms="$("$NM" -D --defined-only "$A/libplayer.so")"
grep -q 'Java_com_molina_player_MPVLib_' <<<"$syms" || fail "simbol JNI com.molina.player tidak ditemukan"
if grep -q 'Java_is_xyz_' <<<"$syms"; then fail "masih ada simbol JNI paket lama"; fi # gate:allow memastikan tidak ada JNI lama

tar -czf "$OUT/molina-mpv-arm64.tgz" -C "$LIBS" arm64-v8a

{
  # shellcheck source=/dev/null
  . "$B/include/depinfo.sh"
  echo "ndk=$NDK_VERSION ($NDK_REVISION)"
  echo "lua=$v_lua unibreak=$v_unibreak harfbuzz=$v_harfbuzz fribidi=$v_fribidi freetype=$v_freetype"
  echo "mbedtls=$v_mbedtls libxml2=$v_libxml2 fontconfig=$v_fontconfig curl=$v_curl ffmpeg_tag=$v_ci_ffmpeg"
  for d in dav1d ffmpeg freetype2 libass libplacebo mpv; do
    echo "$d=$(git -C "$B/deps/$d" rev-parse HEAD)"
  done
  sed -n 's/^Commit *: */upstream_build_scripts_commit=/p' "$ROOT/player/PROVENANCE.txt"
  echo "tanggal_utc=$(date -u +%Y-%m-%dT%H:%M:%SZ)"
} > "$OUT/BUILD-INFO.txt"

( cd "$OUT" && sha256sum molina-mpv-arm64.tgz libs/arm64-v8a/*.so > SHA256SUMS )
echo "Selesai: $OUT/molina-mpv-arm64.tgz"
