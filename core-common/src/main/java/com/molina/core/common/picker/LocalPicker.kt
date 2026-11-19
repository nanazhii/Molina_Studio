package com.molina.core.common.picker

import android.content.Context
import java.io.File

/** Yang dipilih pengguna di [LocalPicker]. */
enum class PickKind { FILE, FOLDER }

/**
 * Kontrak pemilih berkas/folder lokal Molina. Fitur (mis. pemutar) hanya mengenal kontrak ini; implementasi
 * (file manager Molina) dipasang :app lewat [LocalPickerRegistry], sehingga fitur tidak bergantung pada
 * modul file manager dan pemilih bisa diganti tanpa menyentuh fitur.
 */
interface LocalPicker {
    /**
     * Menampilkan pemilih. [startDir] = folder awal bila ada. [onResult] dipanggil tepat sekali di main thread:
     * berkas (untuk [PickKind.FILE]) atau folder (untuk [PickKind.FOLDER]) yang dipilih, atau null bila dibatalkan.
     */
    fun pick(context: Context, kind: PickKind, startDir: File?, onResult: (File?) -> Unit)
}

/** Titik suntik [LocalPicker], pola sama dengan StreamResolverRegistry. */
object LocalPickerRegistry {
    @Volatile private var picker: LocalPicker? = null

    fun install(picker: LocalPicker?) {
        this.picker = picker
    }

    /** Pemilih terpasang, atau null bila belum ada (pemanggil wajib menangani). */
    fun get(): LocalPicker? = picker
}
