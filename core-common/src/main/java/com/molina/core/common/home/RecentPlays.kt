package com.molina.core.common.home

/** Satu entri riwayat putar. [target] = tautan atau jalur berkas; [title] = teks tampilan. */
data class RecentEntry(val target: String, val title: String, val atMs: Long)

/** Penyimpanan teks mentah riwayat. Implementasi Android (SharedPreferences) ada di :player. */
interface RecentStore {
    fun read(): String
    fun write(text: String)
}

/**
 * Riwayat "Terakhir diputar": terbaru di atas, tanpa duplikat (target sama dipindah ke atas), dibatasi [max].
 * Murni JVM supaya mudah diuji; tidak tahu apa-apa tentang layar maupun mpv.
 */
class RecentPlays(private val store: RecentStore, private val max: Int = DEFAULT_MAX) {

    fun list(): List<RecentEntry> = decode(store.read())

    fun record(target: String, title: String, atMs: Long): List<RecentEntry> {
        val t = target.trim().take(MAX_TARGET)
        if (t.isEmpty()) return list()
        val head = RecentEntry(t, title.trim().ifEmpty { t }.take(MAX_TITLE), atMs)
        val next = (listOf(head) + list().filter { it.target != t }).take(max)
        store.write(encode(next))
        return next
    }

    fun remove(target: String): List<RecentEntry> {
        val next = list().filter { it.target != target }
        store.write(encode(next))
        return next
    }

    fun clear() = store.write("")

    companion object {
        const val DEFAULT_MAX = 30
        private const val MAX_TARGET = 2000
        private const val MAX_TITLE = 200

        /** Satu baris per entri: waktu, judul, target dipisah TAB; TAB/baris baru/backslash di-escape. */
        fun encode(list: List<RecentEntry>): String =
            list.joinToString("\n") { "${it.atMs}\t${esc(it.title)}\t${esc(it.target)}" }

        fun decode(text: String): List<RecentEntry> =
            text.lineSequence().mapNotNull { line ->
                val p = line.split('\t', limit = 3)
                val at = p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                val title = p.getOrNull(1) ?: return@mapNotNull null
                val target = p.getOrNull(2)?.let(::unesc)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                RecentEntry(target, unesc(title), at)
            }.toList()

        private fun esc(s: String): String =
            s.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r")

        private fun unesc(s: String): String {
            val sb = StringBuilder(s.length)
            var i = 0
            while (i < s.length) {
                val c = s[i]
                if (c == '\\' && i + 1 < s.length) {
                    when (s[i + 1]) {
                        't' -> sb.append('\t')
                        'n' -> sb.append('\n')
                        'r' -> sb.append('\r')
                        else -> sb.append(s[i + 1])
                    }
                    i += 2
                } else {
                    sb.append(c)
                    i++
                }
            }
            return sb.toString()
        }
    }
}

/** Judul tampilan riwayat dari target: tautan -> host + segmen terakhir, berkas -> nama berkas. Tanpa query/kredensial. */
object RecentTitle {
    fun of(target: String): String {
        val t = target.trim()
        if (t.contains("://")) {
            val rest = t.substringAfter("://").substringBefore('?').substringBefore('#')
            val host = rest.substringBefore('/').substringAfterLast('@')
            val last = rest.substringAfter('/', "").trimEnd('/').substringAfterLast('/')
            return if (last.isEmpty()) host else "$host · $last"
        }
        return t.substringBefore('?').trimEnd('/').substringAfterLast('/').ifEmpty { t }
    }
}
