# Catatan Clean-Room Molina

Seluruh source Molina Terminal dan Molina Cinema ditulis ulang dari nol.
Repo resmi proyek lain hanya dipelajari sebagai referensi alur dan arsitektur.
Tidak ada berkas, kelas, atau potongan kode yang disalin ke repo ini.

## Referensi yang dipelajari (tanggal 2026-11-12)

| Repo | Commit referensi |
|---|---|
| mpv | bcde6b93cca4 |
| mpv-android | ebf1b3f0cbe9 |
| termux-app | 8629e632fcb9 |
| proot-distro | f8abdd9957f8 |
| material-android | 66b0eeb53afe |

## Aturan
- Referensi dibaca di luar repo (`~/ref`), tidak pernah dimasukkan ke Molina_Studio.
- Implementasi memakai nama package `com.molina.*` dan nama kelas sendiri.
- `scripts/telemetry_gate.py` menggagalkan build jika URL atau identitas dev lama muncul.
- Komponen pihak ketiga yang di-link (mis. libmpv, FFmpeg) dicantumkan beserta lisensinya
  di layar "Tentang dan Lisensi".
