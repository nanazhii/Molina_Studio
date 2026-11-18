#!/bin/sh
# Memasang mesin ekstraksi yt-dlp untuk Molina Player di Debian. Aman dijalankan ulang.
# Jalankan di Molina Terminal:  sh ~/.molina/pasang_ytdlp.sh
set -e
apt-get update
apt-get install -y python3 python3-venv python3-pip
[ -x /root/molina-venv/bin/python ] || python3 -m venv /root/molina-venv
/root/molina-venv/bin/pip install -U --pre "yt-dlp[default]"
mkdir -p /root/.config/molina
/root/molina-venv/bin/python -m yt_dlp --version
echo "Selesai. Putar ulang tautan TikTok atau Facebook di Molina Player."
