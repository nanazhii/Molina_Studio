#!/usr/bin/env bash
# [NDK-PIN] Pin NDK ada di native/pins.env (NDK_VERSION, NDK_REVISION). Dua mode, aman diulang:
#   bash scripts/ensure_ndk.sh                 job APK (Gradle): tidak ada kode native yang dikompilasi
#                                              Gradle, jadi hanya memeriksa pin. Bila sebuah modul
#                                              mendeklarasikan ndkVersion, harus sama dengan pin.
#   bash scripts/ensure_ndk.sh --dest <folder> pipeline native: unduh zip NDK dipin lewat
#                                              native_wget.sh (HTTPS, SHA-256 dari native/pins.sha256),
#                                              ekstrak ke <folder>/android-ndk-<NDK_VERSION>, cek revisi.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# shellcheck source=/dev/null
. "$ROOT/native/pins.env"
: "${NDK_VERSION:?NDK_VERSION kosong di native/pins.env}" "${NDK_REVISION:?NDK_REVISION kosong di native/pins.env}"

check_rev() { grep -qF "$NDK_REVISION" "$1/source.properties"; }

if [ "${1:-}" = "--dest" ]; then
  DEST="${2:?pakai: ensure_ndk.sh --dest <folder>}"
  mkdir -p "$DEST"; DEST="$(cd "$DEST" && pwd)"
  D="$DEST/android-ndk-$NDK_VERSION"
  if [ -d "$D" ] && check_rev "$D"; then echo "NDK $NDK_VERSION ($NDK_REVISION) sudah ada di $DEST."; exit 0; fi
  export NATIVE_PINS_FILE="${NATIVE_PINS_FILE:-$ROOT/native/pins.sha256}"
  export NATIVE_HASH_LOG="${NATIVE_HASH_LOG:-$DEST/download-sha256.txt}"
  [ -s "$NATIVE_PINS_FILE" ] || { echo "native/pins.sha256 kosong: unduhan NDK tanpa verifikasi ditolak" >&2; exit 1; }
  rm -rf "$D"
  Z="$DEST/android-ndk-$NDK_VERSION-linux.zip"
  bash "$ROOT/scripts/native_wget.sh" "https://dl.google.com/android/repository/android-ndk-$NDK_VERSION-linux.zip" -O "$Z"
  unzip -q "$Z" -d "$DEST"; rm -f "$Z"
  [ -d "$D" ] || { echo "folder $D tidak muncul setelah ekstrak" >&2; exit 1; }
  check_rev "$D" || { echo "revisi NDK bukan $NDK_REVISION" >&2; exit 1; }
  echo "NDK $NDK_VERSION ($NDK_REVISION) terpasang di $D."
  exit 0
fi

# Mode job APK: cari ndkVersion yang dideklarasikan modul Gradle mana pun.
DECL="$(grep -rho --include='build.gradle.kts' --include='build.gradle' 'ndkVersion *=* *"[0-9.]*"' "$ROOT" 2>/dev/null | grep -o '[0-9][0-9.]*' | sort -u || true)"
if [ -z "$DECL" ]; then
  echo "Tidak ada ndkVersion di modul Gradle (pustaka native dipasang prebuilt). Pin NDK: $NDK_VERSION ($NDK_REVISION)."
  exit 0
fi
for v in $DECL; do
  [ "$v" = "$NDK_REVISION" ] || { echo "::error::ndkVersion $v di Gradle tidak sama dengan pin $NDK_REVISION (native/pins.env)" >&2; exit 1; }
done
SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
[ -n "$SDK" ] || { echo "::error::ANDROID_HOME tidak diatur" >&2; exit 1; }
if [ -d "$SDK/ndk/$NDK_REVISION" ]; then echo "NDK $NDK_REVISION sudah ada."; exit 0; fi
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --licenses >/dev/null 2>&1 || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" --install "ndk;$NDK_REVISION"
test -d "$SDK/ndk/$NDK_REVISION"
echo "NDK $NDK_REVISION siap."
