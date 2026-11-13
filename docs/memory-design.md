# Desain memori Molina

Satu proses untuk semuanya (Cinema/mpv, Berkas, Terminal, Setelan), ditambah proses anak PTY/proot.
Perangkat uji: Android 8, arm64, RAM terbatas. Aturannya: apa pun yang berat harus punya batas.

## Batas yang berlaku
| Area | Batas | Keterangan |
|---|---|---|
| mpv | 1 sesi aktif | `MpvPlayerSession` wajib `release()`; dilepas juga lewat `ExitHooks` saat Keluar |
| mpv cache | 32 MiB maju, 8 MiB mundur | `demuxer-max-bytes`, `demuxer-max-back-bytes`; seek mundur jauh perlu baca ulang |
| Terminal transcript | 2000 baris | `DEFAULT_TERMINAL_TRANSCRIPT_ROWS` |
| Antrean keluaran PTY | 16 x 32 KiB (maks 512 KiB) | backpressure ke thread baca |
| molina.log | 1 MB + 1 berkas rotasi | `FileLogSink` |
| Log Viewer | 1500 baris | `LogViewerFragment.MAX_LINES` |
| Layanan notifikasi | tanpa objek berat | hanya notifikasi; sesi dipegang `TerminalSessionHolder` |

## Pemantauan
`app.trim` (BEHAV) dicatat tiap sistem memanggil `onTrimMemory`: `level`, `java_mb`, `native_mb`.
Level 20 = UI tersembunyi, 40 = latar, 60 = sedang, 80 = hampir dibunuh. Pakai ini untuk menyetel
batas di atas dari perangkat nyata, tanpa adb.

## Keluar total
Tombol Keluar: kait modul (mpv `release`) -> tutup sesi terminal -> SIGSTOP lalu SIGKILL semua proses
UID (ulang sampai kosong) -> flush log -> matikan proses. `app.exit.swept` mencatat `killed`, `leftover`,
`ms`; `leftover` > 0 dicatat sebagai BUG `app.exit-leftover`.

## Rencana berikutnya (belum dikerjakan)
- Lepas sesi mpv bila dijeda lebih dari N menit dan tab Cinema tidak terlihat.
- Setelan batas cache mpv dan baris transcript terminal.
- Kunci jumlah sesi terminal aktif.
