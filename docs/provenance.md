# Asal-Usul Kode dan Lisensi (Provenance)

Molina dibangun sebagai rebranding dan adaptasi dari beberapa proyek sumber terbuka.
Dokumen ini menggantikan catatan clean-room sebelumnya, karena sebagian kode upstream
dapat diadaptasi ke repo ini. Dicatat per 2026-11-12.

## Upstream

| Upstream | Commit referensi | Lisensi | Status adaptasi |
|---|---|---|---|
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
- Komponen native yang di-link (mis. proot, 7-Zip) dicantumkan beserta lisensinya
  di layar "Tentang dan Lisensi".

## Lisensi aplikasi Molina

GPL-3.0-only (ditetapkan pemilik, 2026-11-12). Alasan: sebagian upstream
berlisensi GPLv3-only sehingga hasil gabungannya tidak dapat memakai "or later".

## Pin build native

Sumber build native dipin di `native/pins.env` (NDK), `native/proot.pins.env` (proot, talloc), dan `native/pins.sha256` (hash unduhan).
Ubah sadar dan catat di progres.md.

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

- Modul `files` juga memakai `androidx.exifinterface:exifinterface` 1.3.7 (Apache-2.0; rotasi EXIF pratinjau gambar). Versi dipin di `gradle/libs.versions.toml`. <!-- 26k5g:d02 -->
- [ ] Verifikasi lisensi tiap POM, lalu centang dan catat tanggalnya di sini.
- [ ] Hasilkan `gradle/verification-metadata.xml` (workflow Verifikasi Gradle) setelah dependensi ini masuk.

## 7-Zip bawaan (Patch 27r)

| Komponen | Versi | Asal | Lisensi |
|---|---|---|---|
| `lib7zz.so` | 7-Zip 26.05 | `7zzs` statis dari rilis resmi 7z2605-linux-arm64.tar.xz (ip7z/7zip), DIMODIFIKASI: tiga `svc #0` set_robust_list menjadi `nop` (seccomp Android 8), skrip `scripts/sevenzip/build_lib7zz.py` | GNU LGPL, sebagian BSD 2/3-clause, dekoder RAR dengan batasan unRAR (tidak dipakai membuat RAR) |

Hash tarball, biner asli, dan hasil dipin di `scripts/sevenzip/pins.sha256` dan diperiksa `SevenZipBundlePinTest`. Teks lisensi: `app/src/main/assets/licenses/7zip-License.txt`.

## Editor Code (port dari repo lama, 2026-11-17)

Sumber: https://github.com/nanazhii/Molina-Studio (`feature/code`, `feature/preview`, subset `core/exec`), diadaptasi
sebagai modul `code` dengan fitur terminal/vault/proyek dibuang.

| Komponen | Lisensi | Catatan |
|---|---|---|
| sora-editor 0.23.6 (`editor`, `language-textmate`) | LGPL-2.1-or-later | Dependensi Maven Central, tanpa modifikasi. Dipin 0.23.6 (compileSdk 35, Kotlin 2.0) |
| Grammar TextMate java, kotlin, python, javascript, xml, html, lua, markdown | dari contoh sora-editor 0.23.6 | `code/src/main/assets/textmate/` |
| Grammar json, shell, css; tema abyss, solarized_dark | MIT (vscode) | Salinan `LICENSE-vscode-MIT.txt` ikut di assets |
| Tema darcula | tidak dipakai | Asal Eclipse tm4e (EPL), tidak kompatibel dengan GPL |
| JetBrains Mono, Fira Code 6.2 | SIL OFL 1.1 | `code/src/main/assets/fonts/` bersama berkas lisensi |
| Jetpack Compose (BOM 2024.12.01), activity-compose 1.9.3 | Apache 2.0 | Dependensi Maven |

URL provenance di dalam metadata grammar TextMate (mis. `github.com/microsoft/...`) tidak diubah; `scripts/telemetry_gate.py`
melonggarkan aturan URL hanya untuk `code/src/main/assets/textmate/` (`VENDORED_PREFIXES`).

