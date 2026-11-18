# Arsitektur Modular Molina

Satu peran per modul. Dilarang menjadi "god module".

| Lapisan | Modul | Peran |
|---|---|---|
| app | `app` | Cangkang: MainActivity, nav 4 tab (Berkas, Code, Terminal, Setelan), wiring |
| feature | `files`, `terminal`, `settings`, `code` | UI dan ViewModel per tab |
| api | `terminal-api` | Kontrak murni (interface, event, state). Tanpa implementasi |
| engine | `terminal-core`, `terminal-emulator`, `terminal-view`, `terminal-pty`, `filemanager` | Implementasi di balik kontrak (proses terminal, PTY, PRoot) |
| core | `core-common`, `core-log`, `core-storage`, `core-ui`, `core-security` | Utilitas lintas fitur |

`native/` bukan modul Gradle: hanya pin dan profil untuk build pustaka native.

## Aturan (ditegakkan `scripts/module_gate.py`)
- core hanya boleh bergantung ke core lain.
- api hanya bergantung ke core.
- engine bergantung ke core dan api miliknya.
- feature bergantung ke core, api miliknya, dan (sementara) engine miliknya. Target: hanya api, engine disuntik lewat `app`.
- Tidak ada feature -> feature.

## Catatan
- Kontrak dipindah antarmodul tanpa mengubah package (mis. `com.molina.terminal.core`) agar impor tetap aman.

## Modul lain
- `terminal-proot`: lingkungan Debian PRoot dan manajemen proses.

## Injeksi sesi terminal

- `terminal` mendefinisikan `TerminalSessionFactory` dan `TerminalSessionFactoryOwner`; `app` (MainActivity + `TerminalWiring.kt`) menyuplai pabrik yang membuat `PtyTerminalSession`.
- Sesi hidup di `TerminalSessionHolder` (ViewModel Activity): pindah tab dan rotasi tidak mematikan shell; sesi selesai diganti baru saat tab dibuka lagi.
- Sementara tipe kembalian pabrik masih `PtyTerminalSession` (dependensi `terminal` -> `terminal-core` belum dilepas).

## Dependensi antar-engine

- `terminal-view` dan `terminal-core` boleh bergantung ke `terminal-emulator` (tipe emulator dan kontrak `MolinaTerminalSession`).
- `terminal-view` dan `terminal-emulator` tidak boleh bergantung ke `terminal-core`; arah: view -> emulator <- core.
- `terminal` (feature) boleh bergantung ke `terminal-core` dan `terminal-view`.
- `terminal` boleh bergantung ke `filemanager` (sumber file manager; hanya bergantung ke `core-ui` dan `core-log`, aksi shell lewat `FileDrawerView.Host`).
- `terminal-core` boleh bergantung ke `terminal-pty` (JNI PTY: fork/exec, ukuran jendela, waitFor).

## Keamanan

`core-security` (kontrak Shield, kebijakan URL, proxy lokal, pemindai) dijelaskan di `docs/security.md`. Modul ini murni JVM dan hanya bergantung ke `core-log`.

## Editor Code

Modul `code` (feature) adalah port editor dari repo lama: Sora-editor + TextMate, ditulis dengan Jetpack Compose dan
dihosting `CodeFragment` lewat `ComposeView`. Compose hanya dipakai di modul ini. Sesi editor hidup di
`CodeSessionHolder` (ViewModel Activity) agar tab terbuka bertahan saat pindah tab.
- Hanya bergantung ke `core-common`; tidak ada feature -> feature (pratinjau dan antarmuka eksekutor dilebur ke modul ini).
- `ScriptExecutor`, `StreamExecutor`, `RunStore` adalah titik suntik dari `app`. Selama belum dipasang: Periksa Python/Shell,
  Python langsung (LSP), dan Jalankan di panel Keluaran redup/tidak aktif.
- Fitur lama yang sengaja tidak dibawa: palet perintah, folder proyek dan riwayat proyek, aksi file (entri menu), kirim ke
  Script Execution, simpan ke Vault, sisipkan/jalankan ke Terminal, snippet Terminal, impor inbox nano.
- Notifikasi perubahan dipertahankan; model datanya kini `guard/ChangeEntry.kt` (dulu `ProjectLog`).
