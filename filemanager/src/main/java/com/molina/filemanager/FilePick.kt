// molina-picker
package com.molina.filemanager

import java.io.File

/**
 * [molina-picker] Mode pilih untuk file manager tertanam: ketuk berkas memanggil [onFile] (folder tetap
 * dibuka/ditutup seperti biasa). Memilih folder lewat [FileManagerEmbed.Handle.activeDirectory].
 * Tanpa [FilePick] file manager berperilaku seperti sebelumnya.
 */
class FilePick(val onFile: (File) -> Unit)
