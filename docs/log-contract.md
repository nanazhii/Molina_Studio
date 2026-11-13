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

Baris chip kedua memuat `Semua sesi | Term t1 | Cinema p1 | ...` (teal untuk sesi Terminal `t*`, amber untuk Cinema/mpv `p*`) (id dari tag `Molina/<area>/<id>`, 8 terakhir). Memilih sesi menampilkan semua area dengan id itu (mis. `term/t1` dan `proot/t1`) dan bisa digabung dengan filter jenis. Tombol "Salin tampilan" menyalin baris build, keterangan filter, dan maksimal 300 baris yang sedang tampil, siap ditempel ke chat saat melapor bug.

## Penamaan kode
`MolinaEvents.bug/behavior` otomatis mengawali kode dengan area bila belum ada
(`MolinaEvents.bug("proot", "install-failed", ...)` -> `[BUG:proot.install-failed]`).

## Kode baku: mpv
| Kode | Jenis | Arti | Kolom |
|---|---|---|---|
| `mpv.load` / `mpv.loaded` | BEHAV | perintah load / berkas berhasil dimuat | `file` (nama saja) |
| `mpv.state` | BEHAV | perubahan status pemutar | `state` |
| `mpv.play` / `mpv.pause` / `mpv.release` | BEHAV | perintah pengguna / sesi dilepas | - |
| `mpv.ended` | BEHAV | pemutaran selesai | - |
| `mpv.error` | BUG | kegagalan dari lapisan pemutar | `code` |
| `mpv.native` | BUG | log internal libmpv level FATAL/ERROR (rate-limit) | `prefix`, `level` |
| `mpv.native-suppressed` | BUG (W) | jumlah log libmpv yang dibuang pembatas | `count` |
| `mpv.session-start` | BEHAV | sesi pemutar dibuat (id p1, p2, ...; libmpv dimuat, observer log terpasang) | - |
| `mpv.surface-attach` / `mpv.surface-detach` | BEHAV | Surface video dipasang / dilepas | - |
| `mpv.native-missing` | BUG | libmpv/libplayer tidak termuat saat sesi dibuka | - |
| `mpv.init-failed` | BUG | MPVLib.create/init melempar exception | - |
| `mpv.release-failed` | BUG | MPVLib.destroy melempar exception | - |

Path dan URL media tidak pernah dicatat; hanya nama berkas.

## Kode baku: term
Tag `Molina/term/<id>`; id sesi (`t1`, `t2`, ...) sama untuk semua peristiwa satu sesi.

| Kode | Jenis | Arti | Kolom |
|---|---|---|---|
| `term.session.start` | BEHAV | sesi dibuat; `why` hanya ada bila jatuh ke shell Android (`rootfs-missing`, `proot-not-ready`, `prepare-failed`) | `kind`, `why` |
| `term.pty-start` | BEHAV | proses berhasil dijalankan di PTY | `pid`, `cmd`, `cols`, `rows` |
| `term.resize` | BEHAV | ukuran berubah (hanya bila berbeda) | `cols`, `rows` |
| `term.close` | BEHAV | sesi ditutup pengguna/aplikasi | `running` |
| `term.exit` | BEHAV | keluar normal (0), atau non-nol setelah 5 detik | `code`, `uptime_ms` |
| `term.spawn-failed` | BUG | proses gagal dijalankan (exec/PTY) | `error` |
| `term.native-missing` | BUG | pustaka native PTY tidak termuat | `error` |
| `term.exit-early` | BUG | keluar non-nol sebelum 5 detik (mis. proot gagal) | `code`, `uptime_ms` |
| `term.proot-assert` | BUG | proot abort assertion IS_IN_SYSENTER (seccomp/kernel lama) | `code`, `uptime_ms` |
| `term.killed` | BUG | dihentikan sinyal (SIGSEGV, SIGKILL oleh LMK, dst.) | `signal`, `uptime_ms` |
| `term.exit-unknown` | BUG | kode keluar tidak bisa dibaca | `uptime_ms` |

Penutupan oleh pengguna (`close`) tidak pernah menghasilkan BUG. Isi input/output dan environment
tidak dicatat.

## Kode baku: proot
Tag `Molina/proot/<id>` memakai id sesi yang sama dengan `term` (`t1`, ...), jadi satu sesi Debian
terbaca berurutan: `term.session.start`, `proot.rootfs-checked`, `proot.guest-files`,
`proot.launch-built`, `term.pty-start`, lalu `term.exit` atau `term.exit-early`.

| Kode | Jenis | Arti | Kolom |
|---|---|---|---|
| `proot.install.start` / `done` / `cancelled` | BEHAV | pemasangan rootfs | - |
| `proot.rootfs-checked` | BEHAV | rootfs terbaca, bash dan env ada | `readable`, `bash`, `env` |
| `proot.guest-files` | BEHAV | berkas dasar guest (resolv.conf, hosts, apt-conf) yang baru ditulis | `created` (atau `none`) |
| `proot.launch-built` | BEHAV | perintah proot disusun, binary lolos pemeriksaan | `arm64`, `loader`, `libs`, `seccomp` |
| `proot.install-failed` | BUG | pemasangan gagal | - |
| `proot.prepare-failed` | BUG | exception saat prepareGuest/build | - |
| `proot.rootfs-unreadable` | BUG | folder rootfs tidak terbaca | `readable`, `bash`, `env` |
| `proot.rootfs-no-shell` | BUG | bash tidak ada di rootfs (sesi akan keluar cepat) | `readable`, `bash`, `env` |
| `proot.prompt-failed` | BUG | gagal menulis skrip/pengaturan prompt (sesi tetap jalan, prompt bawaan Debian) | - |
| `proot.rootfs-no-env` | BUG | usr/bin/env tidak ada di rootfs | `readable`, `bash`, `env` |
| `proot.binary-missing` | BUG | binary proot tidak ditemukan | - |
| `proot.binary-not-executable` | BUG | binary proot tidak punya izin eksekusi | - |
| `proot.binary-wrong-arch` | BUG | binary proot bukan arm64 atau tidak terbaca | `arm64`, `loader`, `libs`, `seccomp` |

## Kode baku: files
Tag `Molina/files`. Path tidak pernah dicatat: hanya `depth` (jumlah segmen path relatif, root = 0),
`count`, jenis galat, kelas exception, dan nama errno baku. Stack trace tidak dilampirkan.

| Kode | Jenis | Arti | Kolom |
|---|---|---|---|
| `files.list` | BEHAV | folder berhasil dibuka | `depth`, `count` |
| `files.permission-missing` | BEHAV | izin penyimpanan belum ada saat membuka folder (keadaan normal pertama kali) | `depth` |
| `files.permission-request` | BEHAV | pengguna menekan Beri izin | - |
| `files.permission-result` | BEHAV | hasil dialog izin | `granted` |
| `files.list-failed` | BUG (W untuk NOT_FOUND/INVALID_PATH) | folder gagal dibaca | `kind`, `error`, `depth`, `errno` |
| `files.layout-failed` | BUG | folder standar Molina gagal dibuat | `kind`, `error`, `depth`, `errno` |

`files.list-failed` dengan `kind=PERMISSION_DENIED` padahal izin sudah diberikan berarti penyebabnya
di luar dialog izin (mis. path bukan milik Molina atau volume dicabut); lihat `errno`.

## Kode baku: app
| Kode | Jenis | Arti | Kolom |
|---|---|---|---|
| `app.nav` | BEHAV | pengguna pindah tab (cinema, files, terminal, settings) | `tab`, `from` (`start` bila belum ada tab sebelumnya) |
| `app.exit.requested` | BEHAV | pengguna menekan Keluar di notifikasi; semua sesi ditutup lalu proses dimatikan | `via` |
| `app.exit.swept` | BEHAV | sisa proses milik aplikasi dimatikan sebelum keluar | `killed` |
| `app.exit-failed` | BUG | langkah keluar gagal (proses aplikasi tetap dimatikan) | - |
| `app.session-service-failed` | BUG | layanan notifikasi gagal dijalankan | - |

## Kode baku: debug (hanya build debug)
Tag `Molina/debug`. Dipicu dari Setelan > tombol `[DEBUG]`, yang hanya dibuat bila `BuildConfig.DEBUG`.

| Kode | Jenis | Arti | Kolom |
|---|---|---|---|
| `debug.crash-trigger` | BEHAV | tepat sebelum crash uji dipicu | `source` |
| `debug.test-bug` | BUG | BUG terkontrol, proses tidak mati | `source` |

Menguji alur CRASH di perangkat: (1) Setelan > Picu CRASH, aplikasi menutup; (2) buka lagi, Log Viewer chip
CRASH harus menampilkan `[CRASH:uncaught] ... thread=main root=java.lang.RuntimeException` dengan stack
trace; (3) Tentang > Salin laporan masalah harus memuat "Crash terbaru" dan "Urutan perilaku sebelum crash"
yang berakhir di `debug.crash-trigger` lalu `CRASH uncaught`. Area crash uji adalah `settings`.

