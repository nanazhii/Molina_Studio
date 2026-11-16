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

Feature (Cinema, Terminal, Setelan) hanya memakai antarmuka ini. `LocalShieldProxy` disuntik dari `:app`.

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
- Tidak mencakup rtmp/rtsp (tidak lewat proxy HTTP) dan proses lain seperti yt-dlp di Debian.
- Daftar pelacak statis dan kecil (`TrackerDomains.kt`); belum bisa diperbarui.
- Shield menyaring tujuan, bukan isi. Konten berbahaya di host publik yang sah tidak terdeteksi.
- Aplikasi lain di perangkat yang sudah punya akses jaringan tidak tersentuh Shield.

## Rencana penyambungan (belum dikerjakan)
1. `:app` membuat `LocalShieldProxy`, memanggil `start()` sebelum mpv diinisialisasi.
2. Cinema memasang `http_proxy` lewat `stream-lavf-o` dan `demuxer-lavf-o` (nilai berawalan panjang `%n%nilai`) beserta `protocol_blacklist`.
3. Bila `failClosed()` true, Cinema menolak sumber jarak jauh.
4. Setelan: sakelar Shield, pelacak, LAN, daftar pengecualian, kartu ringkasan dari `snapshot` dan `recentBlocks()`.
5. Pengumpul `AppFootprint` di lapisan Android, pemindaian dijalankan atas permintaan pengguna.
