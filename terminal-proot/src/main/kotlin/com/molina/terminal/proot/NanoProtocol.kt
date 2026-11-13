// molina-patch21
package com.molina.terminal.proot

/**
 * Kontrak antara pembungkus nano (skrip di Debian) dan baris tombol nano di aplikasi. JVM murni agar bisa diuji.
 * Teks prompt "Pilih [...]" di molina-run.sh dan parser di sini WAJIB diubah bersamaan.
 */
object NanoProtocol {
    /** Judul sesi yang dipasang pembungkus selama nano terbuka (dihapus lagi sesudahnya). */
    const val TITLE_PREFIX = "nano: "

    /** Judul sesi yang dipasang molina-run selama dialog periksa-skrip menunggu jawaban. */
    const val RUN_TITLE_PREFIX = "nano-run: "

    enum class Mode { EDIT, SAVE, NAME, RUNFIX }

    data class Choice(val key: Char, val label: String, val isDefault: Boolean)

    /** Urutan kontrol bawaan nanorc Molina. */
    object Seq {
        const val SAVE = "\u0013"
        const val RUN = "\u001br"
        const val UNDO = "\u001a"
        const val REDO = "\u0019"
        const val FIND = "\u0006"
        const val REPLACE = "\u0012"
        const val SELECT_ALL = "\u0001"
        const val GOTO = "\u0007"
        const val FIX = "\u001bf"
        const val QUIT = "\u0011"
        const val YES = "y"
        const val NO = "n"
        const val CANCEL = "\u0003"
        const val ENTER = "\r"
    }

    private val SCRIPT_EXT = setOf("py", "sh", "bash", "js", "mjs", "cjs", "pl", "rb", "lua", "php")
    private val NAME_PROMPTS = listOf("File Name to Write", "Write to File", "Write Selection to File")
    private val OPTIONS = Regex("""Pilih \[([^\]]*)\]""")
    private val DEFAULT = Regex("""Enter =\s*(\S)\)""")
    private const val VALID = "123aex"

    fun isEditorTitle(title: String?): Boolean = title?.startsWith(TITLE_PREFIX) == true

    fun isRunDialogTitle(title: String?): Boolean = title?.startsWith(RUN_TITLE_PREFIX) == true

    fun fileName(title: String?): String = title?.removePrefix(TITLE_PREFIX).orEmpty()

    /** true bila ekstensi berkas dikenal sebagai skrip yang bisa dijalankan molina-run (tombol RUN dan FIX tampil). */
    fun isScript(fileName: String): Boolean = fileName.substringAfterLast('.', "").lowercase() in SCRIPT_EXT

    /** Prompt nano di tiga baris bawah layar: simpan perubahan, atau nama berkas tujuan. */
    fun detectEditPrompt(bottomRows: List<String>): Mode {
        var found = Mode.EDIT
        for (t in bottomRows) {
            if (t.contains("Save modified buffer", ignoreCase = true)) found = Mode.SAVE
            else if (NAME_PROMPTS.any { t.contains(it, ignoreCase = true) }) found = Mode.NAME
        }
        return found
    }

    fun labelOf(key: Char): String = when (key) {
        '1' -> "PERBAIKI" // perbaiki sementara, berkas tidak diubah
        '2' -> "BERKAS" // perbaiki berkas (cadangan .bak)
        'a' -> "SELALU" // seperti 1, dan jangan tanya lagi
        '3' -> "ADANYA" // jalankan apa adanya
        'e' -> "EDIT" // edit lagi di nano
        'x' -> "BATAL"
        else -> key.toString().uppercase()
    }

    /** Urutan yang dikirim untuk satu pilihan: tombol + Enter (molina-run membaca satu baris). */
    fun sequenceOf(choice: Choice): String = "" + choice.key + "\r"

    /**
     * Cari prompt "Pilih [...]" terbawah di [rows]. Prompt yang terlipat ke baris berikutnya (layar sempit) disambung.
     * Null bila tidak sah (token di luar 1/2/a/3/e/x, atau tanpa 3 dan x) agar tombol tak pernah mengirim jawaban
     * yang tidak ditawarkan.
     */
    fun parseRunChoices(rows: List<String>): List<Choice>? {
        for (i in rows.indices.reversed()) {
            if (!rows[i].contains("Pilih")) continue
            val joined = rows[i] + rows.getOrElse(i + 1) { "" }
            val m = OPTIONS.find(joined) ?: continue
            val keys = m.groupValues[1].split('/')
            if (keys.any { it.length != 1 || it[0] !in VALID }) continue
            if (keys.distinct().size != keys.size) continue
            if ("3" !in keys || "x" !in keys) continue
            val def = DEFAULT.find(joined, m.range.last)?.groupValues?.get(1)?.get(0)
            return keys.map { Choice(it[0], labelOf(it[0]), it[0] == def) }
        }
        return null
    }
}
