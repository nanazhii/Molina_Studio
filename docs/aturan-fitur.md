# Aturan fitur Molina

Berlaku untuk setiap fitur baru (mpv, terminal, dan seterusnya). Ditegakkan CI lewat `scripts/module_gate.py`
dan `scripts/log_gate.py`.

1. **Satu fitur, satu tanggung jawab.** Satu fitur = satu paket (mis. `player/mpv/`, `player/stream/`, `player/search/`)
   dengan satu alasan untuk berubah. Fitur baru tidak ditambahkan ke `MPVActivity`; activity hanya menyediakan
   host (kontrak ke layar) dan memanggil fitur.
2. **Lintas modul lewat kontrak.** Bila fitur dipakai modul lain (mis. terminal memakai pencatat atau resolver),
   kontraknya (interface, data murni, registry) ada di `core-common`; implementasi disuntik oleh `app`.
   Tidak ada feature -> feature dan tidak ada engine yang dikenal langsung oleh feature lain.
3. **Perintah mpv hanya lewat `player/mpv/`** (`MpvGateway.command`; property dan opsi lewat `MpvProperties`). `MPVLib.command/setProperty*/getProperty*/...`
   di luar paket itu ditolak gerbang R2 (baseline hanya boleh turun).
4. **Setiap fitur tercatat di logcat.** Pakai `Diag.behavior(area, kode, ...)` untuk alur yang berhasil dan
   `Diag.bug(area, kode, ...)` untuk kegagalan yang dikenali (kontrak di `core-common`; `app` meneruskan ke
   `MolinaEvents`). `android.util.Log` langsung ditolak gerbang R1.
5. **Kode log terdaftar.** Tambahkan kode ke `docs/LOG_CODES.md` pada commit yang sama (gerbang R3).
   Satu kode = satu makna; format `area.kode`.
6. **Privasi log.** Jangan mencatat URL, jalur, judul pencarian, cookie, atau header. Samarkan lewat
   `PlaybackLog.target()` (URL: skema+host, berkas: nama saja).
7. **Mudah dilacak.** Satu kegagalan = satu baris `[BUG:area.kode] ringkasan | kunci=nilai` yang memuat konteks
   cukup (nama perintah, durasi, sumber, mesin). Cari di logcat: `adb logcat | grep "\[BUG:"` atau
   `grep "\[BEHAV:mpv"`.

Daftar periksa fitur baru: paket sendiri, kontrak bila lintas modul, perintah mpv lewat gateway, kode log
terdaftar, entri `progres.md`, tes JVM untuk logika murni di `core-common`.
