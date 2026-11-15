// molina-27p
package com.molina.core.ui

/** Label ekstensi pada ikon berkas: "skrip.tar.gz" -> "GZ", "README" / ".bashrc" -> "". Maks 4 huruf. */
object ExtLabel {
    fun of(name: String): String {
        val dot = name.lastIndexOf('.')
        if (dot <= 0 || dot == name.length - 1) return ""
        return name.substring(dot + 1).uppercase().take(4)
    }
}
