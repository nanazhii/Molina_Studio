# Asal-Usul Kode dan Lisensi (Provenance)

Molina dibangun sebagai rebranding dan adaptasi dari beberapa proyek sumber terbuka.
Dokumen ini menggantikan catatan clean-room sebelumnya, karena sebagian kode upstream
dapat diadaptasi ke repo ini. Dicatat per 2026-11-12.

## Upstream

| Upstream | Commit referensi | Lisensi | Status adaptasi |
|---|---|---|---|
| mpv | bcde6b93cca4 | GPLv2+ (LGPLv2.1+ bila dibangun dengan -Dgpl=false) | Belum ada berkas diadaptasi |
| mpv-android | ebf1b3f0cbe9 | MIT | MPVLib.kt (mpv-native, hanya package diganti). Build native memakai sumber upstream + patch JNI/FFmpeg lewat scripts/native_mpv.sh |
| NewPipe | 9486b0148af8 | GPLv3 | Belum ada berkas diadaptasi |
| termux-app | 8629e632fcb9 | GPLv3-only; bagian turunan Android-Terminal-Emulator berlisensi Apache 2.0 (README upstream hanya menunjuk terminal-emulator dan terminal-view, berkas tanpa header lisensi, jadi diperlakukan GPLv3-only) | terminal-emulator (lihat bagian Berkas adaptasi) |
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

GPL-3.0-only (ditetapkan pemilik, 2026-11-12). Alasan: sebagian upstream
berlisensi GPLv3-only sehingga hasil gabungannya tidak dapat memakai "or later".
Lisensi libmpv dan FFmpeg bergantung pada opsi build; periksa sebelum rilis.

## Pin build native (P3)

Sumber build native dipin di native/pins.env (commit git) dan native/pins.sha256 (hash unduhan).
Aturan: dependensi yang di upstream mpv-android mengambang (mpv, dav1d, libass, libplacebo) dipin ke
commit terakhir SEBELUM waktu commit mpv-android yang dipin (2026-11-08T14:12:38+01:00).
Karena itu commit mpv yang DIBANGUN (8bc468896ec7) berbeda dari commit referensi di tabel di atas
(bcde6b93cca4, HEAD saat repo ini disiapkan). Tabel = acuan baca; pins.env = yang benar-benar dikompilasi.
FFmpeg n9.0.2 (946fcce07b6d). FreeType dipin ke tag VER-2-15-0; SHA dicatat di BUILD-INFO.txt.

## Berkas adaptasi: termux-app

Sumber: terminal-emulator/src/main/java/com/termux/terminal @ 8629e632fcb9 -> modul `terminal-emulator` (package com.molina.terminal.emulator).
Berkas: AndroidUtils.java, ByteQueue.java, ITermImage.java, KeyHandler.java, Logger.java, TerminalBitmap.java, TerminalBuffer.java, TerminalColorScheme.java, TerminalColors.java, TerminalEmulator.java, TerminalOutput.java, TerminalRow.java, TerminalSessionClient.java, TerminalSixel.java, TextStyle.java, WcWidth.java.
Uji unit upstream ikut diadaptasi (package sama). Tidak diadaptasi: TerminalSession.java dan JNI.java (diganti terminal-core dan terminal-pty), jni/termux.c (-> terminal-pty).
TerminalSessionClient: tipe TerminalSession diganti TerminalOutput.

## Berkas adaptasi: termux-app (terminal-view)

Sumber: terminal-view/src/main @ 8629e632fcb9 -> modul `terminal-view` (package com.molina.terminal.view).
Berkas Java: GestureAndScaleRecognizer.java, TerminalRenderer.java, TerminalView.java, TerminalViewClient.java, PopupWindowCompatGingerbread.java, CursorController.java, TextSelectionCursorController.java, TextSelectionHandleView.java; drawable handle seleksi (warna diganti teal); strings.
Perubahan: TerminalSession -> MolinaTerminalSession (interface baru di terminal-emulator).

## Berkas adaptasi: termux-app (terminal-pty)

Sumber: terminal-emulator/src/main/jni/termux.c dan JNI.java @ 8629e632fcb9 -> modul `terminal-pty` (molina_pty.c, PtyNative.java).
Perubahan: nama simbol JNI dan makro; logika PTY tidak diubah. PtyProcess.kt dan CMake adalah kode baru Molina.
- `PtyTerminalSession.kt` (terminal-core): kode baru Molina; pola thread baca/tulis dan antrean mengikuti TerminalSession.java upstream, tanpa menyalin kode.

<!-- molina-patch26k -->
## Dependensi eksternal: arsip (Patch 26k)

Modul `core-storage` memakai tiga pustaka pihak ketiga untuk ZIP, 7z, dan tar. Hanya pola desain repo
lama yang diikuti; kode mesin (`ArchiveEngine`, `ArchiveNames`) ditulis ulang untuk Molina.

| Pustaka | Versi | Untuk | Lisensi (verifikasi dari POM di Maven Central sebelum rilis) |
|---|---|---|---|
| `net.lingala.zip4j:zip4j` | 2.11.5 | ZIP, ZIP bersandi AES-256 | Apache-2.0 |
| `org.apache.commons:commons-compress` | 1.27.1 | 7z, tar, gz, bz2, xz | Apache-2.0 |
| `org.tukaani:xz` | 1.10 | dekoder/enkoder xz dan LZMA2 | domain publik / 0BSD |

- [ ] Verifikasi lisensi tiap POM, lalu centang dan catat tanggalnya di sini.
- [ ] Hasilkan `gradle/verification-metadata.xml` (workflow Verifikasi Gradle) setelah dependensi ini masuk.
