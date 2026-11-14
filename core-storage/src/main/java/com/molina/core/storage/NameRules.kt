// molina-patch26a
package com.molina.core.storage

import com.molina.core.common.ErrorKind
import com.molina.core.common.MolinaError
import com.molina.core.common.MolinaResult
import java.io.File
import java.nio.file.Files

/** Aturan nama berkas/folder. Pesan galat sengaja tanpa nama (lihat docs/penamaan.md). */
object NameRules {
    const val MAX_LENGTH = 255

    /** Mengembalikan nama yang sudah di-trim, atau INVALID_NAME. */
    fun validate(raw: String): MolinaResult<String> {
        val n = raw.trim()
        val bad = n.isEmpty() || n == "." || n == ".." || n.contains('/') || n.contains('\u0000') ||
            n.length > MAX_LENGTH
        return if (bad) {
            MolinaResult.Failure(MolinaError(ErrorKind.INVALID_NAME, "Nama tidak valid"))
        } else {
            MolinaResult.Success(n)
        }
    }

    internal fun present(f: File): Boolean = f.exists() || try {
        Files.isSymbolicLink(f.toPath())
    } catch (e: Exception) {
        false
    }

    /** Nama bebas bentrok di [dir]: "nama (1).ext", "nama (2).ext", dst. */
    fun unique(dir: File, name: String): File {
        var f = File(dir, name)
        if (!present(f)) return f
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (true) {
            f = File(dir, "$base ($i)$ext")
            if (!present(f)) return f
            i++
        }
    }
}
