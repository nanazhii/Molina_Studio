# Keamanan Molina (modul `core-security`)

Jaringan dianggap tidak tepercaya. `core-security` adalah kontrak bersama dan implementasi dasar
Molina Shield. Modul ini murni JVM (tanpa Android), sehingga seluruh logikanya diuji di `jvm-tests`.

## Kontrak (`ShieldContract.kt`)
| Tipe | Peran |
|---|---|
| `MolinaShield` | Kontrak Shield: `start/stop`, `updateConfig`, `endpoint()`, `failClosed()`, `snapshot`, `recentBlocks()` |
| `ShieldConfig` | Sakelar: `enabled`, `blockTrackers`, `allowLan`, `stripReferer`, `trackerAllowlist` |
| `ProxyEndpoint` | Alamat proxy lokal + token; `toString()` menyembunyikan token |
| `ThreatScanner<T>` | Kontrak pemindai murni: data masuk, `List<Finding>` keluar |
| `Finding` | Temuan: `kind`, `code`, `severity`, `subject` (host/paket saja), `detail` |

Feature (Terminal, Setelan) hanya memakai antarmuka ini. `LocalShieldProxy` disuntik dari `:app`.

## Alur `LocalShieldProxy`
Proxy HTTP di `127.0.0.1`, port acak, wajib token (Basic, dibandingkan konstan-waktu). Per permintaan:
1. token benar, kalau tidak 407;
2. hanya `GET`/`HEAD`/`CONNECT`, tanpa badan dan tanpa `Transfer-Encoding`;
3. `UrlPolicy` (skema, userinfo, host non-ASCII, loopback, metadata cloud, jaringan lokal), port sensitif, daftar pelacak;
4. DNS di-resolve di proxy dengan batas 5 detik, tiap IP diperiksa ulang (`ShieldPolicy.checkAddress`), lalu menyambung ke IP yang sama (anti DNS rebinding);
5. http diteruskan tanpa `Cookie`/`Proxy-*`; https lewat `CONNECT` (TLS tetap ujung ke ujung, isi tidak dilihat).

`stop()` menutup semua soket aktif. Log hanya memuat host dan alasan (satu baris per host per menit), tidak pernah URL, token, atau isi.

## Pemindai (`AppRiskAnalyzer`)
Heuristik atas `AppFootprint` (izin + paket SDK). Hasilnya petunjuk, bukan vonis malware:
`spy.surveillance-combo` (HIGH), `spy.many-sensitive` (MEDIUM), `spy.control-sensitive` (LOW), `tracker.sdk` (LOW).
Aplikasi sistem dilewati. Pengumpul data Android (`PackageManager`) belum dibuat.

## Batas yang diketahui
- Hanya http/https. Protokol lain (mis. ssh, rtmp) dan proses di Debian yang mengabaikan variabel proxy tidak tersaring.
- Daftar pelacak statis dan kecil (`TrackerDomains.kt`); belum bisa diperbarui.
- Shield menyaring tujuan, bukan isi. Konten berbahaya di host publik yang sah tidak terdeteksi.
- Aplikasi lain di perangkat yang sudah punya akses jaringan tidak tersentuh Shield.

## Penyambungan
1. `:app` membuat `LocalShieldProxy` dan memanggil `start()` saat proses mulai (`ShieldBootstrap`).
2. Setelan: sakelar Shield, pelacak, LAN, Referer, ringkasan dari `snapshot` dan `recentBlocks()` (SELESAI: Setelan > Keamanan, `ShieldPrefs`). Daftar pengecualian pelacak belum punya UI.
3. Pengumpul `AppFootprint` di lapisan Android, pemindaian dijalankan atas permintaan pengguna (belum dikerjakan).

## Cakupan diperluas (Patch 2)
| Jalur | Perlakuan |
|---|---|
| yt-dlp, pip, apt di Debian | `ChildProcessProxy.env(endpoint)` menghasilkan `http_proxy`/`https_proxy`; PRoot berbagi jaringan Android. Belum dipasang ke sesi terminal (opsi opt-in) |
| Daftar pelacak | bawaan + berkas lokal `/storage/emulated/0/Molina/shield/trackers.txt` (opsional `.sha256`), tanpa server pembaruan |

Uji perangkat wajib: curl https publik, IP LAN (harus ditolak), dan Shield dimatikan lewat prefs.

## Proxy opt-in untuk Terminal/Debian (Patch 3)
Setelan > Terminal > "Lalu lintas Debian lewat Molina Shield" (bawaan mati, berlaku di sesi baru).
Guest berjalan dengan `env -i`, jadi variabel proxy ditulis ke `etc/profile.d/molina-shield.sh` di rootfs (dibaca `bash --login`),
bukan lewat argumen proses: token tidak tampil di `ps`. Berkas dibuat ulang tiap sesi, hanya dapat dibaca pemilik.

| Keadaan | Hasil |
|---|---|
| opt-in mati, Shield mati, atau Shield tidak ada | berkas dihapus, lalu lintas langsung |
| opt-in + Shield berjalan | `http_proxy`/`https_proxy` ke proxy lokal; `localhost,127.0.0.1,::1` dikecualikan (server pengembangan lokal tetap jalan) |
| opt-in + Shield menyala tetapi gagal start | variabel ke `127.0.0.1:9` (port mati): alat gagal tersambung, tidak keluar tanpa saringan |

Batas: ssh dan git+ssh tidak lewat proxy HTTP; program yang mengabaikan variabel proxy tidak tersaring; hanya GET/HEAD
untuk http biasa (POST http ditolak 405, https lewat CONNECT tidak terbatas); sesi yang sudah berjalan memakai token lama
dan perlu sesi baru setelah Shield dimulai ulang; bila penulisan berkas gagal, dicatat `BUG proot.shield-env-failed` dan lalu lintas langsung.
Diuji dengan curl sungguhan (403 untuk pelacak dan IP metadata, gagal tersambung saat fail-closed).

## Petunjuk di layar (Patch 4)
| Tempat | Petunjuk |
|---|---|
| Setelan > Keamanan | Kartu ringkasan berbingkai tipis; teks teal saat Shield aktif, amber saat dimatikan atau fail-closed |
| Prompt Debian | `[SHIELD] ` bila Shield menyaring sesi, `[SHIELD!] ` bila fail-closed. Dibaca dari `MOLINA_SHIELD` (ditulis `molina-shield.sh`), bukan dari `https_proxy` buatan pengguna |
