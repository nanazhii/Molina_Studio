package com.molina.player.home

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.molina.core.common.diag.Diag
import com.molina.core.common.home.ChannelFileName
import com.molina.core.common.home.M3uParser
import java.io.File

internal sealed interface ImportResult {
    /** [publicFolder] false = disimpan di penyimpanan aplikasi karena Molina/channels tidak bisa ditulis. */
    class Ok(val fileName: String, val count: Int, val publicFolder: Boolean) : ImportResult
    data object Empty : ImportResult
    data object Failed : ImportResult
}

/**
 * Menyalin berkas daftar pilihan pengguna (SAF) ke folder saluran supaya terbaca pada pemuatan berikutnya,
 * tanpa izin URI yang harus dijaga. Blocking: panggil dari IO. Tidak tahu soal tampilan.
 */
internal class ChannelsImporter(
    context: Context,
    private val publicFolder: File,
    private val privateFolder: File,
) {
    private val resolver = context.applicationContext.contentResolver

    fun import(uri: Uri): ImportResult {
        val text = try {
            resolver.openInputStream(uri)?.use { it.readTextLimited(M3uFetcher.MAX_BYTES) }
        } catch (e: Exception) {
            Diag.bug("player", "channels-failed", "berkas pilihan gagal dibaca", e, mapOf("source" to "import"))
            null
        } ?: return ImportResult.Failed

        val count = M3uParser.parse(text).size
        if (count == 0) return ImportResult.Empty

        val name = ChannelFileName.of(displayName(uri))
        val inPublic = write(publicFolder, name, text)
            ?: write(privateFolder, name, text)?.let { false }
            ?: return ImportResult.Failed
        Diag.behavior("player", "channels-imported", "daftar saluran diimpor", mapOf(
            "count" to count.toString(), "public" to inPublic.toString()))
        return ImportResult.Ok(name, count, inPublic)
    }

    /** true bila tertulis; null bila gagal. Berkas bernama sama ditimpa (impor ulang = perbarui). */
    private fun write(dir: File, name: String, text: String): Boolean? = try {
        dir.mkdirs()
        File(dir, name).writeText(text, Charsets.UTF_8)
        true
    } catch (e: Exception) {
        Diag.bug("player", "channels-failed", "berkas daftar gagal ditulis", e, mapOf("source" to "write"))
        null
    }

    private fun displayName(uri: Uri): String? = try {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment
    } catch (e: Exception) {
        uri.lastPathSegment
    }
}
