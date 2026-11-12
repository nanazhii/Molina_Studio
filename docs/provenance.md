# Asal-Usul Kode dan Lisensi (Provenance)

Molina dibangun sebagai rebranding dan adaptasi dari beberapa proyek sumber terbuka.
Dokumen ini menggantikan catatan clean-room sebelumnya, karena sebagian kode upstream
dapat diadaptasi ke repo ini. Dicatat per 2026-11-12.

## Upstream

| Upstream | Commit referensi | Lisensi | Status adaptasi |
|---|---|---|---|
| mpv | bcde6b93cca4 | GPLv2+ (LGPLv2.1+ bila dibangun dengan -Dgpl=false) | Belum ada berkas diadaptasi |
| mpv-android | ebf1b3f0cbe9 | MIT | Belum ada berkas diadaptasi |
| NewPipe | 9486b0148af8 | GPLv3 | Belum ada berkas diadaptasi |
| termux-app | 8629e632fcb9 | GPLv3-only (terminal-emulator dan terminal-view: Apache 2.0; lihat pengecualian upstream) | Belum ada berkas diadaptasi |
| proot-distro | f8abdd9957f8 | GPLv3 | Belum ada berkas diadaptasi |
| Material Files | 93c333960aa8 | GPLv3 | Belum ada berkas diadaptasi |
| material-components-android | 66b0eeb53afe | Apache 2.0 | Dipakai sebagai dependensi dan referensi |

Kolom "Status adaptasi" wajib diperbarui per berkas (nama berkas, upstream asal, commit)
setiap kali kode diadaptasi.

## Aturan

- Pertahankan header hak cipta dan lisensi upstream pada berkas yang diadaptasi,
  walau package diganti ke com.molina.*.
- Tandai baris atribusi yang memuat identitas lama dengan komentar `gate:allow atribusi`
  agar tidak ditolak oleh scripts/telemetry_gate.py.
- Berkas hasil adaptasi mengikuti lisensi upstream asalnya.
- Komponen yang di-link (libmpv, FFmpeg, dan lainnya) dicantumkan beserta lisensinya
  di layar "Tentang dan Lisensi".

## Lisensi aplikasi Molina

GPL-3.0-only (usulan, konfirmasi pemilik sebelum rilis). Alasan: sebagian upstream
berlisensi GPLv3-only sehingga hasil gabungannya tidak dapat memakai "or later".
Lisensi libmpv dan FFmpeg bergantung pada opsi build; periksa sebelum rilis.
