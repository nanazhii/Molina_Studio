# Arsitektur Modular Molina

Satu peran per modul. Dilarang menjadi "god module".

| Lapisan | Modul | Peran |
|---|---|---|
| app | `app` | Cangkang: MainActivity, nav 4 tab (Cinema, Berkas, Terminal, Setelan), wiring |
| feature | `cinema`, `files`, `terminal`, `settings` | UI dan ViewModel per tab |
| api | `player-api`, `terminal-api` | Kontrak murni (interface, event, state). Tanpa implementasi |
| engine | `mpv-native`, `terminal-core`, `terminal-emulator` | Implementasi di balik kontrak (JNI mpv, proses terminal) |
| core | `core-common`, `core-log`, `core-storage`, `core-ui` | Utilitas lintas fitur |

`native/` bukan modul Gradle: hanya pin dan profil untuk build pustaka native.

## Aturan (ditegakkan `scripts/module_gate.py`)
- core hanya boleh bergantung ke core lain.
- api hanya bergantung ke core.
- engine bergantung ke core dan api miliknya.
- feature bergantung ke core, api miliknya, dan (sementara) engine miliknya. Target: hanya api, engine disuntik lewat `app`.
- Tidak ada feature -> feature.

## Catatan
- Kontrak dipindah antarmodul tanpa mengubah package (`com.molina.cinema`, `com.molina.terminal.core`) agar JNI `Java_com_molina_cinema_MPVLib_*` dan impor tetap aman.
- Jangan ubah nama/package `MPVLib` tanpa menyamakan `scripts/native_mpv.sh`.

## Modul yang direncanakan (belum dibuat)
- `player-mpv`: implementasi `MolinaPlayerSession` di atas `MPVLib`.
- `terminal-proot`: lingkungan Debian PRoot dan manajemen proses.
