# Molina_Studio

Lisensi: GPL-3.0-only. Lihat LICENSE dan docs/provenance.md.

## Application ID

Application ID aplikasi: `com.molina` (sebelumnya `com.molina.studio`).

Alasan: repo lama (Molina-Studio) memakai `com.molina.studio` dengan tanda tangan berbeda, sehingga APK repo ini
gagal dipasang di atasnya (konflik paket / tanda tangan tidak cocok). `com.molina` adalah identitas paket baru
yang terpisah dari APK repo lama.

- Yang berubah hanya `applicationId` (identitas di perangkat). `namespace` Kotlin/R tetap `com.molina.studio`
  dan nama kelas tidak berubah, jadi tidak ada pemindahan berkas.
- JNI mpv (`com.molina.cinema.MPVLib`) tidak terpengaruh.
- Data aplikasi lama tidak ikut: ini aplikasi baru di mata Android. Copot `com.molina.studio` bila tidak dipakai lagi.
- Jangan ubah `applicationId` lagi tanpa alasan kuat: mengubahnya membuat pembaruan atas APK yang sudah terpasang mustahil.

## Log perilaku (pengganti adb logcat)
Molina mencatat perilaku aplikasi ke `molina.log` agar bug di perangkat bisa dilacak dari
perilaku nyata, tanpa PC. Aturan untuk setiap fitur baru:
- Catat `BEHAV` pada titik keputusan (sesi mulai, berkas dimuat, pemasangan selesai), bukan di loop panas.
- Catat `BUG` pada kegagalan yang dikenali kode, lewat `MolinaEvents.bug(area, kode, ringkasan, throwable)`.
- Jangan mencatat isi input/output terminal, path atau URL media penuh, maupun kata sandi.
- Daftar kode per area ada di [docs/log-contract.md](docs/log-contract.md); tambahkan kode baru di sana.
- Baca di aplikasi: Pengaturan > Log, atau salin laporan masalah (Crash / Bug / Urutan perilaku).

