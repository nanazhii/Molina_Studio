// molina-patch13
package com.molina.cinema

/** Privasi log: path dan URL media tidak pernah dicatat penuh, hanya nama berkas. */
internal object MpvLogScrub {
    private val URL_OR_PATH = Regex("""[A-Za-z][A-Za-z0-9+.\-]*://\S+|/(?:[^\s/'"]+/)+[^\s/'"]*""")

    fun fileName(uri: String): String = uri.substringBefore('?').substringAfterLast('/')

    fun scrub(text: String): String =
        URL_OR_PATH.replace(text) { m ->
            fileName(m.value).trimEnd('.', ',', ':', ';', ')').ifEmpty { "<path>" }
        }
}
