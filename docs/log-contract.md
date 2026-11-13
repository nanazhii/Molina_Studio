# Kontrak Log Molina

Tujuan: log aplikasi berperan seperti `adb logcat` di PC, secara 1-1. Baris yang tampil di
Log Viewer, di laporan masalah, di `molina.log`, dan di `adb logcat` adalah baris yang sama,
sehingga masalah di perangkat bisa dianalisis hanya dari salinan laporan, tanpa PC.

## Bentuk baris
2026-11-13 09:12:44.101 E/Molina/term: [CRASH:uncaught] exception tidak tertangani | thread=main root=java.lang.IllegalStateException
java.lang.IllegalStateException: pty putus
at com.molina.terminal.core.PtyTerminalSession.read(PtyTerminalSession.kt:88)

- Tag: `Molina/<area>[/<id>]` (maksimal 23 karakter di logcat). Area = segmen kedua tag; itu
  yang dipakai "Error terbaru per area".
- Pesan peristiwa: `[JENIS:kode] ringkasan | kunci=nilai kunci=nilai`, satu baris. Stack trace
  mengikuti pada baris berikutnya.

## Tiga jenis peristiwa

| Jenis | Level | Kapan dipakai | Contoh |
|---|---|---|---|
| `CRASH` | E | Exception tak tertangani; dicatat otomatis oleh `CrashReporter`, area diambil dari stack trace | `[CRASH:uncaught] ... | thread=main root=...` |
| `BUG` | E (boleh W) | Kegagalan spesifik yang dikenali kode | `[BUG:proot.install-failed] unduhan terputus` |
| `BEHAV` | I | Alur penting yang berhasil, untuk merekonstruksi urutan kejadian | `[BEHAV:term.session.start] | kind=debian` |

API: `MolinaEvents.crash/bug/behavior` (core-log). Jangan menulis awalan `[BUG:...]` manual.

## Aturan

- Kode: `<alur>.<peristiwa>` huruf kecil, angka, titik, hubung (`proot.install-failed`). Kode
  stabil: jangan diganti hanya karena ganti redaksi, agar log lama tetap bisa dicari.
- Area baku: `core`, `app`, `term`, `proot`, `mpv`, `cinema`, `files`, `settings`.
- Nilai kolom tanpa spasi (spasi diubah menjadi `_`), maksimal 200 karakter.
- Privasi: tidak mencatat isi input/output terminal maupun path/query penuh media.
- `BEHAV` dicatat pada titik keputusan (sesi mulai, pemasangan selesai), bukan di loop panas.

## Membaca

- Di aplikasi: Pengaturan > Log, atau salin laporan masalah.
- Di PC (bila ada): `adb logcat -d | grep -E "\[(CRASH|BUG)"`.

## Batas

- Crash native (SIGSEGV di libmpv) tidak melewati handler Java. Jejaknya hanya ada di buffer
  `crash` logcat (`logcat -b crash`), yang bisa dibaca bila izin READ_LOGS diberikan.
- Bila logger belum diinisialisasi (crash sangat awal sebelum `Application.onCreate`), peristiwa
  hanya ada di logcat bawaan sistem.

<!-- molina-patch12 -->
## Laporan masalah dan filter Log Viewer

Urutan bagian laporan masalah (Pengaturan > Tentang > Salin laporan masalah):

1. Info build.
2. `Crash terbaru`: sampai 3 crash, terbaru dulu, lengkap dengan stack trace (dipangkas 12 baris).
3. `Bug`: dikelompokkan per kode, urut dari yang terakhir muncul; menampilkan jumlah, jam,
   ringkasan, kolom, dan exception baris pertama dari kejadian terbaru.
4. `Urutan perilaku sebelum crash`: sampai 15 peristiwa `BEHAV`/`BUG` sebelum crash terakhir,
   kronologis, ditutup baris `CRASH ... <-- proses berhenti`. Tanpa crash, bagian ini menjadi
   `Perilaku terakhir (tanpa crash)`.
5. `Error terbaru per area` dan `Log terakhir` (50 entri) seperti sebelumnya.

Bila bagian 4 menulis "tidak ada peristiwa BEHAV/BUG tercatat sebelum crash", titik keputusan di
alur itu belum memanggil `MolinaEvents.behavior`; tambahkan di sana.

Log Viewer: chip `Semua | CRASH | BUG | BEHAV` (dengan jumlah peristiwa) menyaring berdasarkan
penanda. Penanda hanya dikenali pada tag `Molina/...`. Baris stack trace yang mengikuti CRASH/BUG
(pid, tid, dan tag sama) ikut tersaring masuk. Warna: CRASH merah (`molina_error`), BUG amber
(`molina_cinema_accent`), BEHAV teal (`molina_terminal_accent`); baris lain warna bawaan.
