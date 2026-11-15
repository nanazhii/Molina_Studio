# Arsip di Penjelajah Molina (catatan desain 26k)

Dokumen ini mencatat keputusan perilaku yang tidak terlihat dari kode saja. Log: lihat `docs/log-contract.md`.

## Kebijakan berkas yang sudah ada (Patch 26k-4)

`ConflictPolicy` dipilih di dialog "Opsi Ekstrak" (Ekstrak terpilih, atau "Ekstrak ke…" saat menekan "Ekstrak di sini").
Menu cepat "Ekstrak di sini" / "Ekstrak ke folder" selalu RENAME.

| Kebijakan | Perilaku |
|---|---|
| RENAME (bawaan) | Berkas baru diberi nomor: `a (1).txt`. Berkas lama tidak disentuh. |
| SKIP | Berkas yang ada dibiarkan; entri dilewati dan dihitung ke `skipped`. |
| OVERWRITE | Hanya setelah konfirmasi dialog. Berkas lama dipindah (rename atomik) ke `.molina-bak-*` di folder yang sama; berkas baru ditulis; cadangan dihapus hanya setelah SELURUH operasi sukses. Gagal/batal = berkas baru dihapus dan cadangan dikembalikan. |

Pengecualian OVERWRITE (jatuh ke RENAME, tanpa pesan galat): tujuan berupa folder, tujuan berupa tautan simbolik, dan
berkas yang baru ditulis operasi yang sama (entri kembar di dalam satu arsip).

Batas yang diketahui: bila proses dimatikan Android di tengah OVERWRITE, berkas `.molina-bak-*` tertinggal (isinya adalah
berkas lama yang utuh). Belum ada pemulihan otomatis saat startup; usulan: sapu `.molina-bak-*` yang lebih tua dari 1 hari.

## Ekstrak banyak arsip

Satu arsip gagal tidak menghentikan yang lain. Tiap arsip atomik. Dibatalkan = arsip yang sedang berjalan dibersihkan,
arsip yang sudah selesai tetap ada, sisanya tidak dijalankan (`notRun`). Arsip bersandi tidak dihitung gagal: antreannya
(`FilesArchive.passwordQueue`, tanpa kata sandi) diminta satu per satu setelah semua selesai; Batal = lewati arsip itu.

<!-- 26k5a:d01 -->
## Pohon isi arsip (Patch 26k-5a)

`ArchiveTree` (`:core-storage`, logika murni) membangun folder virtual dari daftar entri datar `ArchiveListing`.
Kunci simpul = kunci parameter `only` pada ekstrak: berkas `a/b.txt`, folder `a/b/` (berakhiran `/`).

- Folder implisit disintesis dari jalur berkas. Entri tidak aman (`..`, NUL) dilewati (`skipped`); backslash dinormalkan menjadi `/`.
- Berkas kembar dalam satu arsip: yang terakhir menang (`duplicates`). Berkas `a` dan folder `a/` boleh berdampingan.
- Urutan: folder dulu; lalu nama (tanpa membedakan huruf) atau ukuran terbesar.
- Batas daftar `ArchiveEngineLimits.LIST_MAX` naik dari 5000 ke 50.000. Daftar yang masih terpotong
  (`ArchiveListing.truncated`) menghasilkan pohon tidak lengkap dan UI wajib menampilkan banner. Untuk tar yang
  terpotong, pembacaan berhenti di batas sehingga jumlah total TIDAK diketahui (tidak sama dengan ZIP/7z).
- Pencarian nama hanya mencakup entri yang termuat.

<!-- 26k5b:d01 -->
## Buka satu entri lewat cache (Patch 26k-5b)

`MolinaStorage.openArchiveEntry(relative, entryName, password, cancel, onProgress)` mengekstrak SATU berkas ke cache
`Molina/.arsip-cache/` lalu mengembalikan `OpenedEntry.relative` (relatif root) untuk dibuka lewat FileProvider.
`clearArchiveCache()` mengosongkan cache; panggil saat aplikasi mulai dan keluar (hook UI ada di 26k-5c).

- Memakai `Sink` yang sama dengan ekstrak biasa: nama dibersihkan (`cleanEntry`), jalur kanonik (`safeTarget`), batas
  entri, cek ruang kosong, rollback, pembatalan. Arsip dengan satu entri tidak aman tetap ditolak seluruhnya.
- Batas: `ArchiveEngineLimits.ENTRY_MAX_BYTES` = 150 MB per entri (header dan byte nyata, jadi tar tanpa ukuran header
  juga terjaga), `CACHE_MAX_BYTES` = 300 MB total. Entri yang melebihi batas ditolak dengan `ArchiveFailure.TOO_LARGE`
  dan tidak pernah memecah isi cache.
- Slot cache = folder bernama hash SHA-1(jalur kanonik arsip, ukuran, waktu ubah, nama entri); arsip yang berubah
  otomatis menjadi slot baru. Berkas di dalam slot memakai nama dasar entri (aman dibuka dengan ACTION_VIEW + MIME).
- Ekstrak berjalan di `.tmp-*` lalu di-rename atomik ke slot: gagal/batal tidak meninggalkan slot setengah jadi.
- Pemangkasan LRU menurut waktu ubah slot (disentuh saat dipakai ulang), slot yang baru dibuka tidak pernah dihapus.
- Entri kembar dalam tar: yang terakhir ditulis dipertahankan (selaras dengan ArchiveTree).
- Privasi: kata sandi tidak masuk kunci, nama folder, atau log. Isi entri bersandi tersimpan sebagai berkas biasa di cache
  sampai dipangkas atau cache dikosongkan; slot yang sudah ada dibuka ulang tanpa sandi.
- Satu operasi sekaligus. Dua pembukaan bersamaan untuk entri yang sama bisa saling menghapus slot (UI menserialkan).
- Folder cache disembunyikan dari daftar dan pencarian Berkas walau "tampilkan tersembunyi" aktif.

## Uji arsip

`ArchiveEngine.test` mendekode semua entri ke sink kosong (tanpa tulis disk). Arsip rusak = hasil, bukan exception.
TAR polos tidak punya checksum isi: hanya struktur dan ukuran yang diperiksa (`checksummed=false`). ZIP bersandi dengan
entri rusak bisa dilaporkan sebagai kata sandi salah oleh zip4j (keterbatasan pustaka).

<!-- 26k5c:d01 -->
## Lihat isi arsip (Patch 26k-5c)

Ketuk berkas arsip (atau menu "Lihat isi") membuka lembar bawah `ArchiveSheet`: folder virtual dari `ArchiveTree`,
breadcrumb, pencarian nama (maks. 200 hasil, ditandai bila dipotong), urut nama/ukuran, pilih dengan tekan lama.

- Logika navigasi murni ada di `ArchiveBrowse` (tes JVM); lembar hanya menggambar dan memanggil `MolinaStorage`.
- "Ekstrak terpilih" memakai `ArchiveRequest.only` (kunci folder berakhiran "/"); kunci di dalam folder yang juga dipilih
  dibuang. Tanpa pilihan, menu "Ekstrak semua" mengirim `only=null`. Tujuan dan kebijakan timpa lewat dialog yang sama
  dengan ekstrak biasa; operasi berjalan di `FilesArchive` (satu operasi sekaligus) dan lembar ditutup setelah mulai.
- Buka satu entri: `openArchiveEntry` -> cache -> `ACTION_VIEW` lewat FileProvider (jalur `Molina/` sudah mencakup
  `.arsip-cache`, jadi `molina_file_paths.xml` tidak berubah).
- Daftar dipotong di `LIST_MAX` (50.000): banner kuning yang bisa ditutup, pencarian hanya mencakup entri yang termuat.
- Kata sandi hanya di memori instans lembar: tidak masuk Bundle, log, atau state. Memutar layar membuat lembar dibuat
  ulang dan meminta sandi lagi bila arsip bersandi. Pilihan > 500 item tidak disimpan ke Bundle.
- Cache dikosongkan sekali per proses saat Berkas pertama kali tampil, dan saat aktivitas selesai (`isFinishing`);
  juga lewat menu "Bersihkan cache". Sapuan dilewati bila ada operasi berjalan. Isi entri bersandi bisa tinggal di
  cache selama aplikasi hidup (keputusan A). Kegagalan sapuan hanya dicatat (`files.archive-cache-failed`).
- Log: `files.archive-view`, `files.archive-open`, `files.archive-cache`; kegagalan memakai `files.archive-failed`
  dengan `op=list` atau `op=open` (WARN untuk sandi/TOO_LARGE/NO_MATCH), dan `files.archive-open-failed` untuk
  kegagalan peluncuran aplikasi pembuka.
- 26k-5d menyelesaikan "Ekstrak ke..." dari lembar dan pratinjau gambar (lihat bagian di bawah). <!-- 26k5e:d01 -->

<!-- 26k5d:d01 -->
## Lihat isi: kuota cache, ikon, Ekstrak ke..., pratinjau (Patch 26k-5d)

- Header lembar menampilkan "Cache arsip X / 300 MB" (`archiveCacheBytes`) dengan aksi Bersihkan; diperbarui setelah
  daftar dimuat, setelah entri dibuka, dan setelah dibersihkan. Menutup celah privasi entri bersandi di cache.
- Entri ditolak `TOO_LARGE`: dialog menawarkan "Ekstrak ke folder" (satu entri, kebijakan RENAME, ke folder arsip).
- Ikon kecil per jenis berkas: lambang teks (bukan drawable, tidak ada perubahan res) berwarna menurut `FileKind` bersama
  di `:core-ui`, satu sumber dengan daftar Berkas dan drawer Terminal; folder hijau, jenis tidak dikenal abu-abu. <!-- 26k5e:d02 -->
- "Ekstrak ke..." dari lembar memasang klip arsip dengan `only` (bertahan saat rotasi lewat Bundle; maks. 500 kunci).
  Kata sandi TIDAK ikut klip: arsip bersandi diminta sandinya lewat alur kata sandi biasa saat Ekstrak di sini.
- Gambar (jpg, jpeg, png, webp, bmp, gif) dipratinjau dalam aplikasi: decode `inSampleSize` hingga sisi terpanjang
  2048 px, tanpa membaca rotasi EXIF; ada tombol "Buka di aplikasi lain". Hanya format yang bisa didecode
  BitmapFactory di Android 8 (jpg, jpeg, png, webp, bmp, gif); svg/ico/heic/avif tetap dibuka lewat aplikasi lain.
  Dialog pratinjau ditutup saat lembar dihancurkan (rotasi) supaya jendela tidak bocor. <!-- 26k5e:d03 -->
- Menu ⋮ lembar: item "Ekstrak ke…" berlabel "Ekstrak terpilih ke…" bila ada pilihan dan "Ekstrak semua ke…" bila tidak
  (perilaku sama: tanpa pilihan = seluruh arsip). <!-- 26k5f:d01 -->
