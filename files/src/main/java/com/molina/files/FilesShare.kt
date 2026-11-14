// molina-patch26j
package com.molina.files

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import java.util.Locale

/** Intent Bagikan lewat FileProvider (authority = packageName + ".fileprovider", lihat molina_file_paths.xml). */
internal object FilesShare {
    fun mimeOf(file: File): String {
        val ext = file.extension.lowercase(Locale.ROOT)
        return ShareMime.forExtension(ext)
            ?: MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
            ?: ShareMime.FALLBACK
    }

    /** Melempar IllegalArgumentException bila berkas di luar jalur FileProvider. */
    fun chooser(ctx: Context, files: List<File>): Intent {
        val auth = ctx.packageName + ".fileprovider"
        val uris = ArrayList<Uri>(files.map { FileProvider.getUriForFile(ctx, auth, it) })
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.type = ShareMime.common(files.map { mimeOf(it) })
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(send, "Bagikan").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
}
