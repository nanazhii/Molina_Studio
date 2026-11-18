# Branding Molina

- Terminal: ikon jendela dengan prompt `>_`, gradient hijau `#69F0AE` -> `#00E676` -> `#00BFA5`, latar `#0E1114` -> `#04231D`.
- Wordmark `MOLINA_`: `core-ui/.../drawable/molina_wordmark.xml` (vektor, gradient hijau), dipakai di `molina_splash.xml`.
- Splash: tema `Theme.Molina.Splash` (hanya untuk MainActivity), diganti ke `Theme.Molina.App` lewat `setTheme` sebelum `super.onCreate`. Android 12+ memakai splash sistem dengan ikon peluncur di latar `molina_bg`.
- Ikon notifikasi: `R.drawable.ic_stat_molina_terminal` (modul core-ui); pakai `setSmallIcon(...)` dengan warna aksen `molina_terminal_accent`.

## Ikon dan Tentang
- Satu ikon peluncur: `activity-alias` `.IconGreen` (aktif bawaan) menunjuk `.MainActivity`. Pemilih ikon dan ikon alternatif dihapus; `MolinaApplication` mengembalikan alias ke status bawaan manifest.
- Peluncuran lewat adb memakai `MainActivity` langsung atau salah satu alias, mis. `am start -n com.molina.studio/.MainActivity`.
- Tentang: Setelan -> Tentang menampilkan wordmark, versi (versionName dan versionCode dari PackageManager), dan nama paket.

## Pelacakan build
- Setelan -> Tentang menampilkan `versionName (versionCode) · <7 karakter pertama commit>`.
- Nilainya `BuildConfig.COMMIT_SHA` (modul settings), diambil dari env `GITHUB_SHA` saat build di GitHub Actions. Build di luar CI menampilkan `dev`.
- Cocokkan angka itu dengan `git log --oneline` untuk tahu APK berasal dari commit mana.


## Baris versi di log
- Saat start (dan sekali lagi setelah izin penyimpanan diberikan, ketika log pindah ke folder publik) aplikasi menulis satu baris `Molina <versi> (<kode>) · <commit> · API <level>` dengan tag `Molina/core`.
- Cari dengan: `grep "Molina 0\." /sdcard/Molina/logs/molina.log | tail -1` (sesuaikan nama berkas dengan konfigurasi log).

## Salin info build
- Setelan -> Tentang -> "Salin info build" menyalin baris yang sama dengan log (`Molina <versi> (<kode>) · <commit> · API <level>`) ke clipboard, siap ditempel di laporan masalah.
- Logika baris ada di satu tempat: `BuildLine.describe` (modul settings); `BuildInfo` (app) hanya memanggilnya untuk log.

## Salin laporan masalah
- Setelan -> Tentang -> "Salin laporan masalah" menyalin: baris info build, ringkasan "Error terbaru per area" (term, proot, files, dst., dikelompokkan dari tag `Molina/<area>`), dan 50 entri log terakhir dari `molina.log` (termasuk stack trace, dipotong 12 baris per entri, maksimal 60.000 karakter).
- Sumber: `molina.log` di folder log yang aktif (publik atau privat aplikasi) ditambah `molina.1.log`; logika ada di `LogReport` (murni, diuji `LogReportTest`) dan `LogReportSource` (baca berkas).
- Batas: crash yang mematikan proses tanpa sempat tercatat lewat `MolinaLog` tidak ada di `molina.log`; untuk itu lihat layar Logcat.
