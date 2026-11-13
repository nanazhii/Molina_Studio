# Branding Molina

- Terminal: ikon jendela dengan prompt `>_`, gradient hijau `#69F0AE` -> `#00E676` -> `#00BFA5`, latar `#0E1114` -> `#04231D`.
- Cinema: ikon layar dengan segitiga play, gradient amber `#FFD54F` -> `#FFB300` -> `#FF8F00`, latar `#07090B` -> `#261A00`.
- Wordmark `MOLINA_`: `core-ui/.../drawable/molina_wordmark.xml` (vektor, gradient hijau), dipakai di `molina_splash.xml`.
- Splash: tema `Theme.Molina.Splash` (hanya untuk MainActivity), diganti ke `Theme.Molina.App` lewat `setTheme` sebelum `super.onCreate`. Android 12+ memakai splash sistem dengan ikon peluncur di latar `molina_bg`.
- Ikon notifikasi: `R.drawable.ic_stat_molina_terminal` (modul core-ui); pakai `setSmallIcon(...)` dengan warna aksen `molina_terminal_accent`.
- Ikon Cinema (`@mipmap/ic_launcher_cinema`) disiapkan untuk modul Player yang dipisah nanti; belum dipasang di manifest.

## Pemilih ikon dan Tentang
- Pemilih ikon: Setelan -> Ikon aplikasi (Terminal hijau / Cinema amber). Diimplementasikan dengan dua `activity-alias` (`.IconGreen` aktif bawaan, `.IconAmber` nonaktif) yang menunjuk `.MainActivity`; `IconPicker` (modul settings) menukar status keduanya dengan `DONT_KILL_APP`.
- Label peluncur ikut berganti: "Molina" dan "Molina Cinema". Launcher bisa butuh beberapa detik, dan sebagian launcher menutup pintasan yang sudah ditaruh di layar utama saat ikon berganti.
- Peluncuran lewat adb memakai `MainActivity` langsung atau salah satu alias, mis. `am start -n com.molina.studio/.MainActivity`.
- Tentang: Setelan -> Tentang menampilkan wordmark, versi (versionName dan versionCode dari PackageManager), dan nama paket.
