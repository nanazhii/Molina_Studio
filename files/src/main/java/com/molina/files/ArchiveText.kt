// molina-patch26k2
package com.molina.files

import com.molina.core.common.MolinaError
import com.molina.core.storage.ArchiveException
import com.molina.core.storage.ArchiveFailure
import com.molina.core.storage.ArchiveProgress
import com.molina.core.storage.CompressResult
import com.molina.core.storage.ExtractResult

/** Teks panel progres dan ringkasan hasil Ekstrak/Kompres. Tanpa nama atau path berkas. */
internal object ArchiveText {
    fun verb(op: ArchiveOp): String = when (op) {
        ArchiveOp.EXTRACT -> "Mengekstrak"
        ArchiveOp.EXTRACT_SUBS -> "Mengekstrak subtitle"
        ArchiveOp.COMPRESS -> "Mengompres"
    }

    /** Baris progres, mis. "Mengekstrak 45% \u00B7 12.0 MB". Total ekstrak tar = ukuran arsip (byte terkompresi). */
    fun progressLine(op: ArchiveOp, p: ArchiveProgress?, cancelling: Boolean): String {
        if (cancelling) return "Membatalkan\u2026"
        if (p == null) return verb(op) + "\u2026"
        val pct = (p.fraction * 100).toInt().coerceIn(0, 100)
        val size = if (p.bytesTotal > 0) " \u00B7 " + FileSizeFormat.format(p.bytesTotal) else ""
        return verb(op) + " $pct%" + size
    }

    fun reasonOf(error: MolinaError): ArchiveFailure? = (error.cause as? ArchiveException)?.reason

    fun failure(reason: ArchiveFailure, subs: Boolean = false): String = when (reason) {
        ArchiveFailure.NEED_PASSWORD -> "Arsip butuh kata sandi"
        ArchiveFailure.WRONG_PASSWORD -> "Kata sandi salah"
        ArchiveFailure.UNSUPPORTED -> "Format arsip tidak didukung"
        ArchiveFailure.CORRUPT -> "Arsip rusak atau tidak lengkap"
        ArchiveFailure.NO_SPACE -> "Ruang penyimpanan tidak cukup"
        ArchiveFailure.TOO_MANY_ENTRIES -> "Arsip memuat terlalu banyak entri"
        ArchiveFailure.UNSAFE_ENTRY -> "Arsip berisi jalur tidak aman, dibatalkan"
        ArchiveFailure.NAME_TOO_LONG -> "Nama terlalu panjang (maks. 255 byte)"
        ArchiveFailure.INVALID_NAME -> "Nama arsip tidak valid"
        ArchiveFailure.NAME_CONFLICT -> "Ada dua item bernama sama; kompres secara terpisah"
        ArchiveFailure.OUT_OF_MEMORY -> "Memori tidak cukup; turunkan level kompresi"
        ArchiveFailure.NO_MATCH -> if (subs) "Tidak ada subtitle di arsip ini" else "Tidak ada entri yang cocok"
        ArchiveFailure.EMPTY_SOURCE -> "Tidak ada berkas yang bisa dikompres"
        ArchiveFailure.NOT_FOUND -> "Berkas tidak ditemukan"
        ArchiveFailure.PASSWORD_UNSUPPORTED -> "Format ini belum mendukung kata sandi"
        ArchiveFailure.IO -> "Operasi arsip gagal (periksa izin tulis)"
    }

    fun nameProblem(reason: ArchiveFailure): String =
        if (reason == ArchiveFailure.NAME_TOO_LONG) failure(reason) else failure(ArchiveFailure.INVALID_NAME)

    fun error(error: MolinaError, op: ArchiveOp): String {
        val reason = reasonOf(error)
        return if (reason != null) failure(reason, op == ArchiveOp.EXTRACT_SUBS) else FilesErrorText.forOp(error.kind)
    }

    /** Ringkasan Snackbar: jumlah, ukuran hasil, nama baru, dan jumlah dilewati. */
    fun extractSummary(r: ExtractResult, subs: Boolean): String {
        if (r.cancelled) return "Dibatalkan. Tidak ada yang diekstrak"
        val sb = StringBuilder("${r.files} ${if (subs) "subtitle" else "berkas"} diekstrak")
        if (r.bytes > 0) sb.append(" \u00B7 ").append(FileSizeFormat.format(r.bytes))
        if (r.renamed > 0) sb.append(", ${r.renamed} diberi nama baru")
        if (r.skipped > 0) sb.append(", ${r.skipped} dilewati")
        return sb.toString()
    }

    fun compressSummary(r: CompressResult): String {
        if (r.cancelled) return "Dibatalkan. Arsip tidak dibuat"
        val sb = StringBuilder("Arsip dibuat \u00B7 ${FileSizeFormat.format(r.bytes)} (${r.entries} item)")
        if (r.skipped > 0) sb.append(", ${r.skipped} dilewati")
        return sb.toString()
    }

    fun finished(state: ArchiveState.Finished): String {
        val req = state.request
        state.extract?.let { return extractSummary(it, req.op == ArchiveOp.EXTRACT_SUBS) }
        state.compress?.let { return compressSummary(it) }
        return state.error?.let { error(it, req.op) } ?: "Operasi gagal"
    }
}
