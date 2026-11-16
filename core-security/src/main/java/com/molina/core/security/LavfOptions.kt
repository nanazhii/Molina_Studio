package com.molina.core.security

/**
 * Pembentuk nilai opsi `stream-lavf-o` dan `demuxer-lavf-o` mpv. Format mpv: `kunci=nilai,kunci=nilai`;
 * tiap nilai diberi awalan panjang-tetap `%n%` (n = jumlah byte UTF-8) agar koma di dalam nilai aman.
 * Murni, tanpa Android.
 */
object LavfOptions {
    /** Protokol ffmpeg yang tidak pernah dibutuhkan pemutar dan rawan disalahgunakan (berlaku untuk semua sumber). */
    const val PROTOCOL_BLACKLIST = "concat,concatf,subfile,ftp,sftp,unix,md5,gopher,gophers,pipe,fd,tee"

    /** Tambahan saat arus langsung (rtmp) diblokir penuh, termasuk yang dibuka dari dalam playlist. */
    const val RTMP_BLACKLIST = "rtmp,rtmps,rtmpt,rtmpe,rtmpte,rtmpts"

    /** Satu-satunya protokol yang boleh dibuka lavf saat Shield menyala tetapi proxy mati (fail-closed). */
    const val LOCAL_ONLY_WHITELIST = "file,crypto,data"

    fun fixed(s: String): String = "%" + s.toByteArray(Charsets.UTF_8).size + "%" + s

    fun join(pairs: List<Pair<String, String>>): String = pairs.joinToString(",") { it.first + "=" + fixed(it.second) }

    /**
     * Opsi lavf menurut keadaan Shield. Dibaca SEKALI saat sesi mpv dibuat (sebelum init); bila Shield
     * dimulai ulang, [PlaybackGuard] menolak pemuatan sampai pemutar dibuka ulang.
     * - [shield] null: hanya blacklist dasar.
     * - menyala + proxy hidup: blacklist + `http_proxy`.
     * - menyala + proxy mati: blacklist + whitelist lokal saja (tidak ada lalu lintas jarak jauh).
     */
    fun forShield(shield: MolinaShield?): String {
        val cfg = shield?.config()
        val bl = if (cfg != null && cfg.enabled && cfg.directStreams == DirectStreamPolicy.BLOCK) {
            PROTOCOL_BLACKLIST + "," + RTMP_BLACKLIST
        } else PROTOCOL_BLACKLIST
        val pairs = ArrayList<Pair<String, String>>()
        pairs += "protocol_blacklist" to bl
        if (shield != null && cfg != null && cfg.enabled) {
            val ep = shield.endpoint()
            if (ep != null) pairs += "http_proxy" to ep.url() else pairs += "protocol_whitelist" to LOCAL_ONLY_WHITELIST
        }
        return join(pairs)
    }
}
