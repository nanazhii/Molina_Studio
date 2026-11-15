// molina-27q
package com.molina.core.common

/**
 * [molina-27q] Perintah `7z a` (paket Debian `7zip`) untuk membuat 7z BERSANDI di Debian PRoot. Dipindah dari repo lama
 * (SevenZipCommand, ARCHIVE6) dan dipangkas ke sisi kompres; murni tanpa Android supaya bisa diuji JVM.
 *
 * Aturan yang diverifikasi di repo lama terhadap 7-Zip 23.01 asli:
 * - `-spd` WAJIB: tanpa itu `*`/`?` pada nama diperluas jadi wildcard. `--` mencegah nama berawalan `-` dibaca sebagai opsi.
 * - `-p` tanpa nilai membaca sandi dari stdin (satu baris; non-TTY tidak meminta verifikasi). Sandi TIDAK PERNAH di argumen
 *   proses (terlihat di `ps`) dan tidak di log.
 * - `-mhe=on` mengenkripsi nama berkas juga (daftar isi pun butuh sandi).
 * - Jalur sumber absolut: 7z menyimpan HANYA nama item (`/mnt/molina-in/0/foto` -> `foto/...`).
 * - Sumber tak terbaca membuat 7z keluar 1 TETAPI arsip tetap tertulis; pemanggil wajib menganggapnya gagal.
 */
object SevenZipCommand {
    /** Urut prioritas. `7z` ada di paket `7zip`; `7zz` hanya di `7zip-standalone`. */
    val GUEST_BINARIES = listOf("/usr/bin/7z", "/usr/bin/7zz")

    const val GUEST_PATH = "/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
    const val INSTALL_CMD = "apt-get update && apt-get install -y --no-install-recommends 7zip"
    const val HINT_INSTALL = "7zip belum terpasang di Debian. Di Terminal jalankan: $INSTALL_CMD"

    /** Titik mount berlingkup di guest: HANYA folder sumber dan folder staging yang di-bind, bukan seluruh penyimpanan. */
    const val GUEST_OUT = "/mnt/molina-out"
    const val GUEST_IN = "/mnt/molina-in"

    /** Nama berkas keluaran DI DALAM staging; diberi nama akhir oleh pemanggil saat dipindah. */
    const val OUT_FILE = "archive.7z"
    const val MAX_SOURCE_DIRS = 8

    /**
     * Argumen `7z a` ke [guestOut] dari [guestSources] (jalur guest ABSOLUT). Sumber yang komponen terakhirnya memuat `*` atau `?`
     * DITOLAK: 7-Zip (terukur) membuang sumber lain yang hilang DIAM-DIAM bila ada wildcard walau -spd aktif, dan arsip bersandi
     * yang tidak lengkap tanpa tanda itu berbahaya. [excludes] = jalur dalam arsip (`nama/sub/tautan`) yang dilewati, satu `-x!`
     * per entri (literal karena -spd); dipakai untuk tautan simbolik supaya isi targetnya tidak ikut. `-snl` = lapis kedua.
     */
    fun createArgs(
        guestOut: String, guestSources: List<String>, level: Int, hasPassword: Boolean, encryptNames: Boolean,
        excludes: List<String> = emptyList(),
    ): List<String> {
        requireGuestAbs(guestOut)
        require(guestSources.isNotEmpty()) { "Tidak ada sumber untuk dikompres" }
        guestSources.forEach {
            requireGuestAbs(it)
            val leaf = it.substringAfterLast('/')
            require(leaf.none { c -> c == '*' || c == '?' }) { "Nama berisi * atau ? tidak didukung untuk 7z bersandi: $leaf" }
        }
        require(level in 0..9) { "Level kompresi tidak valid: $level" }
        require(!encryptNames || hasPassword) { "Enkripsi nama berkas membutuhkan kata sandi" }
        excludes.forEach {
            require(it.isNotEmpty() && !it.startsWith("/") && it.none { c -> c == '\u0000' || c == '\n' || c == '\r' }) { "Nama pengecualian tidak valid" }
        }
        return buildList {
            add("a"); add("-t7z"); add("-mx=$level"); add("-snl"); add("-spd"); add("-bsp1"); add("-y")
            if (hasPassword) add("-p")
            if (encryptNames) add("-mhe=on")
            for (e in excludes) add("-x!$e")
            add("--"); add(guestOut)
            addAll(guestSources)
        }
    }

    /** True bila [p] aman dipakai sebagai sisi host `--bind=host:guest` (proot memecah di ':'). */
    fun bindHostOk(p: String): Boolean =
        p.startsWith("/") && p.none { it == ':' || it == '\n' || it == '\r' || it == '\u0000' } && p.split('/').none { it == ".." }

    /**
     * Argv proot (tanpa argv[0]) dengan bind EKSPLISIT [binds] (host -> guest) dan tanpa bind penyimpanan lain: 7z hanya melihat
     * folder sumber dan folder staging. Titik mount guest harus sudah ada di rootfs (dibuat pemanggil).
     */
    fun prootArgsScoped(rootfs: String, binds: List<Pair<String, String>>, binary: String, args: List<String>): List<String> = buildList {
        add("--link2symlink"); add("--kill-on-exit"); add("-0")
        add("--rootfs=$rootfs"); add("--cwd=/root")
        add("--bind=/dev"); add("--bind=/proc"); add("--bind=/sys")
        for ((host, guest) in binds) {
            require(bindHostOk(host)) { "Jalur host tidak valid untuk bind: $host" }
            requireGuestAbs(guest)
            require(guest.indexOf(':') < 0) { "Jalur guest tidak valid untuk bind: $guest" }
            add("--bind=$host:$guest")
        }
        add("/usr/bin/env"); add("-i")
        add("HOME=/root"); add("LANG=C.UTF-8"); add("MALLOC_ARENA_MAX=2")
        add("PATH=$GUEST_PATH")
        add(binary)
        addAll(args)
    }

    /** Bahan stdin untuk sandi: satu baris, atau null bila tanpa sandi. Sandi berisi baris baru ditolak. */
    fun stdinFor(password: String?): ByteArray? {
        val pw = password?.takeIf { it.isNotEmpty() } ?: return null
        require(pw.indexOf('\n') < 0 && pw.indexOf('\r') < 0 && pw.indexOf('\u0000') < 0) { "Sandi tidak boleh memuat baris baru" }
        return (pw + "\n").toByteArray(Charsets.UTF_8)
    }

    private fun requireGuestAbs(p: String) {
        require(p.startsWith("/")) { "Jalur guest harus absolut: $p" }
        require(p.indexOf('\u0000') < 0 && p.indexOf('\n') < 0 && p.indexOf('\r') < 0) { "Jalur tidak valid" }
    }

    enum class Failure(val hint: String) {
        NEED_PASSWORD("Arsip dikunci. Masukkan kata sandi."),
        WRONG_PASSWORD("Kata sandi salah."),
        DISK_FULL("Ruang penyimpanan tidak cukup."),
        OUT_OF_MEMORY("Memori tidak cukup untuk arsip ini."),
        BAD_COMMAND("Perintah 7z ditolak (argumen tidak valid)."),
        CANCELLED("Dibatalkan."),
        SOURCE_UNREADABLE("Sebagian berkas sumber tidak bisa dibaca. Arsip tidak dibuat."),
        UNKNOWN("7z gagal tanpa sebab yang dikenali."),
    }

    /** Kode keluar 7z: 0 sukses, 1 peringatan, 2 fatal, 7 galat perintah, 8 memori, 255 dihentikan. */
    fun classify(exit: Int, output: String, passwordGiven: Boolean): Failure {
        val e = output.lowercase()
        fun has(vararg k: String) = k.any { it in e }
        return when {
            has("enter password") && !passwordGiven -> Failure.NEED_PASSWORD
            has("wrong password") -> Failure.WRONG_PASSWORD
            has("no space left", "disk full", "not enough space", "there is not enough space") -> Failure.DISK_FULL
            exit == 8 || has("can not allocate memory", "cannot allocate memory", "out of memory") -> Failure.OUT_OF_MEMORY
            exit == 7 || has("command line error") -> Failure.BAD_COMMAND
            exit == 255 -> Failure.CANCELLED
            else -> Failure.UNKNOWN
        }
    }

    /** Galat `7z a`: exit 1 (peringatan, mis. sumber hilang/tak terbaca) di sini FATAL karena arsip yang tertulis tidak lengkap. */
    fun classifyCreate(exit: Int, output: String, passwordGiven: Boolean): Failure =
        if (exit == 1) Failure.SOURCE_UNREADABLE else classify(exit, output, passwordGiven)

    private val PCT = Regex("""(\d{1,3})%""")

    /** Persen terakhir (0..100) dari potongan stdout `-bsp1` (berisi backspace dan CR); null bila belum ada. */
    fun parseProgress(chunk: String): Int? {
        val last = PCT.findAll(chunk).lastOrNull() ?: return null
        val v = last.groupValues[1].toIntOrNull() ?: return null
        return if (v in 0..100) v else null
    }
}
