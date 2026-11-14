// molina-patch26c
package com.molina.files

import com.molina.core.storage.BatchResult
import com.molina.core.storage.TransferKind
import com.molina.core.storage.TransferProgress

/** Teks untuk panel progres dan ringkasan hasil operasi massal. Tanpa nama/path berkas. */
internal object TransferText {
    fun verb(kind: TransferKind): String = when (kind) {
        TransferKind.COPY -> "Menyalin"
        TransferKind.MOVE -> "Memindahkan"
        TransferKind.DELETE -> "Menghapus"
    }

    fun clipLabel(kind: TransferKind, count: Int): String = when (kind) {
        TransferKind.MOVE -> "Pindah $count item"
        else -> "Salin $count item"
    }

    /** Baris progres, mis. "Menyalin 2/5 \u00B7 45% \u00B7 12.0 MB". */
    fun progressLine(kind: TransferKind, p: TransferProgress?, cancelling: Boolean): String {
        if (cancelling) return "Membatalkan\u2026"
        if (p == null) return verb(kind) + "\u2026"
        val shown = (p.itemIndex + 1).coerceAtMost(p.itemCount.coerceAtLeast(1))
        val pct = (p.fraction * 100).toInt().coerceIn(0, 100)
        val size = if (p.bytesTotal > 0) " \u00B7 " + FileSizeFormat.format(p.bytesTotal) else ""
        return verb(kind) + " $shown/${p.itemCount} \u00B7 $pct%" + size
    }

    private fun done(kind: TransferKind): String = when (kind) {
        TransferKind.COPY -> "disalin"
        TransferKind.MOVE -> "dipindahkan"
        TransferKind.DELETE -> "dihapus"
    }

    /** Ringkasan akhir untuk Snackbar. */
    fun summary(r: BatchResult): String {
        val ok = "${r.succeeded} item ${done(r.kind)}"
        val skip = if (r.skipped > 0) ", ${r.skipped} dilewati" else ""
        return when {
            r.cancelled -> "Dibatalkan. $ok$skip" + if (r.failed > 0) ", ${r.failed} gagal" else ""
            r.failed > 0 -> {
                val why = r.firstError?.let { FilesErrorText.forTransfer(it.kind) }
                "$ok$skip, ${r.failed} gagal" + if (why != null) ": $why" else ""
            }
            r.succeeded == 0 && r.skipped > 0 -> "Tidak ada yang berubah (${r.skipped} item sudah di folder ini)"
            else -> ok + skip
        }
    }
}
