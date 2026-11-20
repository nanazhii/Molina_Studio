#!/bin/sh
# molina-ytdlp-install
# Pasang yt-dlp (zipapp: SATU berkas Python, tanpa venv/pip/kompilator) untuk Molina Player.
# Terminal: sh ~/.molina/pasang_ytdlp.sh   |   Aplikasi: tombol "Pasang otomatis" di pemutar (skrip yang sama).
# Aman diulang: tahap yang sudah beres dilewati. Baris MOLINA_* di stdout dibaca aplikasi; teks manusia ke stderr.
# Ubah VERSION dan SHA256 bersamaan (nilai sama dengan entri yt-dlp di native/pins.sha256).
VERSION="2026.10.26"
SHA256="e51e69e89489f199530f13d32a4342da3ddb2cf34842f364603a066db05afbf3"
URL="https://github.com/yt-dlp/yt-dlp/releases/download/$VERSION/yt-dlp" # gate:allow unduhan resmi yt-dlp, hash dipin
DIR="$HOME/.molina"
DEST="$DIR/yt-dlp"
TMP="$DEST.part"
export DEBIAN_FRONTEND=noninteractive

msg() { echo "$*" >&2; }
fail() { echo "MOLINA_FAIL $1"; msg "Gagal ($1)."; rm -f "$TMP"; exit 1; }
good() { [ -f "$DEST" ] && [ "$(sha256sum "$DEST" 2>/dev/null | cut -d' ' -f1)" = "$SHA256" ]; }

mkdir -p "$DIR" || fail FAILED

echo "MOLINA_STEP PACKAGES"
if ! command -v python3 >/dev/null 2>&1 || [ ! -s /etc/ssl/certs/ca-certificates.crt ]; then
  msg "Memasang python3 dan ca-certificates (tanpa paket rekomendasi)..."
  apt-get update -qq >/dev/null 2>&1 || fail NO_NETWORK
  apt-get install -y -qq --no-install-recommends python3 ca-certificates >/dev/null 2>&1 || fail NO_PACKAGES
fi

echo "MOLINA_STEP DOWNLOAD"
if good; then
  msg "yt-dlp $VERSION sudah ada, unduhan dilewati."
else
  msg "Mengunduh yt-dlp $VERSION (sekitar 3 MB)..."
  rm -f "$TMP"
  if ! python3 - "$URL" "$TMP" <<'PY' 2>/dev/null
import shutil, sys, urllib.request
req = urllib.request.Request(sys.argv[1], headers={"User-Agent": "molina-pasang/1"})
with urllib.request.urlopen(req, timeout=30) as r, open(sys.argv[2], "wb") as f:
    shutil.copyfileobj(r, f, 65536)
PY
  then
    fail NO_NETWORK
  fi
fi

echo "MOLINA_STEP VERIFY"
if [ -f "$TMP" ]; then
  got=$(sha256sum "$TMP" 2>/dev/null | cut -d' ' -f1)
  [ "$got" = "$SHA256" ] || fail BAD_HASH
  chmod 755 "$TMP" && mv -f "$TMP" "$DEST" || fail FAILED
fi
good || fail FAILED

echo "MOLINA_STEP TEST"
VER=$(python3 -c 'import sys; sys.path.insert(0, sys.argv[1]); import yt_dlp.version as v; print(v.__version__)' "$DEST" 2>/dev/null) || fail FAILED
case "$VER" in
  ""|*[!0-9A-Za-z._-]*) fail FAILED ;;
esac
echo "MOLINA_DONE $VER"
msg "yt-dlp $VER siap."
if [ -d "$HOME/molina-venv" ]; then
  msg "Catatan: venv lama tidak dipakai lagi dan boleh dihapus: rm -rf ~/molina-venv"
fi
exit 0
