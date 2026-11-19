#!/bin/sh
# Memasang mesin ekstraksi yt-dlp untuk Molina Player di Debian. Aman dijalankan ulang.
# Jalankan di Molina Terminal:  sh ~/.molina/pasang_ytdlp.sh
set -e
if ! python3 -c 'import venv, ensurepip' >/dev/null 2>&1; then
  apt-get update
  apt-get install -y python3 python3-venv python3-pip
fi
[ -x /root/molina-venv/bin/python ] || python3 -m venv /root/molina-venv
/root/molina-venv/bin/pip install -U --pre --no-deps yt-dlp certifi
mkdir -p /root/.config/molina
/root/molina-venv/bin/python -m yt_dlp --version
echo "Selesai. Putar ulang tautan TikTok atau Facebook di Molina Player."
