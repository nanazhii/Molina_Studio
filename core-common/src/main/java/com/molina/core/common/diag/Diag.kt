package com.molina.core.common.diag

/**
 * Kontrak pencatatan peristiwa fitur. Modul mana pun (player, terminal, ...) cukup bergantung ke
 * core-common untuk mencatat; implementasi ke logcat dan molina.log dipasang oleh :app saat start,
 * sehingga tidak ada dependensi antar fitur dan tidak ada modul yang mengenal core-log secara langsung.
 *
 * Kode peristiwa wajib terdaftar di docs/LOG_CODES.md (gerbang R3 membaca pemanggilan literal
 * dengan area dan kode sebagai dua argumen pertama).
 */
interface DiagSink {
    fun behavior(area: String, code: String, summary: String, fields: Map<String, String>)
    fun bug(area: String, code: String, summary: String, error: Throwable?, fields: Map<String, String>)
}

object Diag {
    @Volatile private var sink: DiagSink? = null

    /** Pasang (atau cabut dengan null) implementasi pencatat. Dipanggil sekali di Application.onCreate. */
    fun install(sink: DiagSink?) {
        this.sink = sink
    }

    /** Alur penting yang berhasil. Tidak pernah melempar: pencatatan tidak boleh menjatuhkan fitur. */
    fun behavior(area: String, code: String, summary: String = "", fields: Map<String, String> = emptyMap()) {
        try {
            sink?.behavior(area, code, summary, fields)
        } catch (_: Exception) {
            // pencatatan gagal diabaikan
        }
    }

    /** Kegagalan yang dikenali kode. Tidak pernah melempar. */
    fun bug(
        area: String,
        code: String,
        summary: String = "",
        error: Throwable? = null,
        fields: Map<String, String> = emptyMap(),
    ) {
        try {
            sink?.bug(area, code, summary, error, fields)
        } catch (_: Exception) {
            // pencatatan gagal diabaikan
        }
    }
}
